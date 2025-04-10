package dev.vality.disputes.schedule.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.disputes.admin.MerchantsNotificationParamsRequest;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.NotificationDao;
import dev.vality.disputes.domain.enums.NotificationStatus;
import dev.vality.disputes.domain.tables.pojos.Notification;
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

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
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
    @SneakyThrows
    public void process(NotifyRequest notifyRequest) {
        var plainTextBody = customObjectMapper.writeValueAsString(notifyRequest);
        try {
            var forUpdate = checkPending(notifyRequest);
            var httpRequest = new HttpPost(forUpdate.getNotificationUrl());
            httpRequest.setEntity(HttpEntities.create(plainTextBody, ContentType.APPLICATION_JSON));
            httpClient.execute(httpRequest, new BasicHttpClientResponseHandler());
            notificationDao.delivered(forUpdate);
            log.info("Delivered NotifyRequest {}", notifyRequest);
        } catch (IOException ex) {
            log.info("IOException when handle NotificationService.process {}", notifyRequest, ex);
            var forUpdate = checkPending(notifyRequest);
            var dispute = disputeDao.get(UUID.fromString(notifyRequest.getDisputeId()));
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            var nextAttemptAfter = exponentialBackOffPollingService.prepareNextPollingInterval(forUpdate, dispute.getCreatedAt(), providerData.getOptions());
            notificationDao.updateNextAttempt(forUpdate, nextAttemptAfter);
            log.debug("Finish IOException handler {}", notifyRequest, ex);
        } catch (NotificationStatusWasUpdatedByAnotherThreadException ex) {
            log.debug("NotificationStatusWasUpdatedByAnotherThreadException when handle NotificationService.process", ex);
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
