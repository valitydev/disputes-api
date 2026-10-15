package dev.vality.disputes.schedule.core;

import dev.vality.disputes.admin.MerchantsNotificationParamsRequest;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.NotificationDao;
import dev.vality.disputes.domain.enums.NotificationStatus;
import dev.vality.disputes.domain.tables.pojos.Notification;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.exception.NotificationStatusWasUpdatedByAnotherThreadException;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.swag.disputes.model.NotifyRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final DisputeDao disputeDao;
    private final NotificationDao notificationDao;
    private final ProviderDataService providerDataService;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper customObjectMapper;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;

    @Transactional
    public List<NotifyRequest> getNotifyRequests(int batchSize) {
        return notificationDao.getNotifyRequests(batchSize);
    }

    @Transactional
    public void process(NotifyRequest selected) {
        var current = getForProcessing(selected);
        if (current.isEmpty()) {
            return;
        }
        var forUpdate = current.get();
        var notifyRequest = notificationDao.getNotifyRequest(forUpdate.getDisputeId());
        var plainTextBody = customObjectMapper.writeValueAsString(notifyRequest);
        try {
            var httpRequest = new HttpPost(forUpdate.getNotificationUrl());
            httpRequest.setEntity(HttpEntities.create(plainTextBody, ContentType.APPLICATION_JSON));
            httpClient.execute(httpRequest, new BasicHttpClientResponseHandler());
            notificationDao.delivered(forUpdate);
            log.info("Delivered NotifyRequest {}", notifyRequest);
        } catch (IOException ex) {
            log.info("IOException when handle NotificationService.process {}", notifyRequest, ex);
            var dispute = disputeDao.get(UUID.fromString(notifyRequest.getDisputeId()));
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            var nextAttemptAfter =
                    exponentialBackOffPollingService.prepareNextPollingInterval(forUpdate, dispute.getCreatedAt(),
                            providerData.getOptions());
            notificationDao.updateNextAttempt(forUpdate, nextAttemptAfter);
            log.debug("Finish IOException handler {}", notifyRequest, ex);
        }
    }

    private Optional<Notification> getForProcessing(NotifyRequest selected) {
        try {
            var current = checkPending(selected);
            if (current.getNextAttemptAfter().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                log.debug("Skip not due Notification {}", current.getDisputeId());
                return Optional.empty();
            }
            return Optional.of(current);
        } catch (NotificationStatusWasUpdatedByAnotherThreadException ex) {
            log.debug("Skip stale Notification {}", selected.getDisputeId());
            return Optional.empty();
        } catch (NotFoundException ex) {
            if (ex.getType() != NotFoundException.Type.NOTIFICATION) {
                throw ex;
            }
            log.debug("Skip locked or missing Notification {}", selected.getDisputeId());
            return Optional.empty();
        }
    }

    @SneakyThrows
    public void sendMerchantsNotification(MerchantsNotificationParamsRequest params) {
        var dispute = disputeDao.getByInvoiceId(params.getInvoiceId(), params.getPaymentId());
        var notifyRequest = notificationDao.getNotifyRequest(dispute.getId());
        var forUpdate = checkPending(notifyRequest);
        var httpRequest = new HttpPost(forUpdate.getNotificationUrl());
        var plainTextBody = customObjectMapper.writeValueAsString(notifyRequest);
        httpRequest.setEntity(HttpEntities.create(plainTextBody, ContentType.APPLICATION_JSON));
        httpClient.execute(httpRequest, new BasicHttpClientResponseHandler());
        log.info("Delivered NotifyRequest by MerchantsNotificationParamsRequest {}", notifyRequest);
    }

    private Notification checkPending(NotifyRequest notifyRequest) {
        var forUpdate = notificationDao.getSkipLocked(UUID.fromString(notifyRequest.getDisputeId()));
        if (forUpdate.getStatus() != NotificationStatus.pending) {
            throw new NotificationStatusWasUpdatedByAnotherThreadException();
        }
        return forUpdate;
    }
}
