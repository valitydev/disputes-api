package dev.vality.disputes.schedule.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.disputes.admin.MerchantsNotificationParamsRequest;
import dev.vality.disputes.api.converter.NotifyRequestConverter;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.NotificationDao;
import dev.vality.disputes.dao.model.EnrichedNotification;
import dev.vality.disputes.domain.enums.NotificationStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.Notification;
import dev.vality.disputes.exception.NotificationStatusWasUpdatedByAnotherThreadException;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.schedule.service.ProviderDataService;
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

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class NotificationService {

    private final DisputeDao disputeDao;
    private final NotificationDao notificationDao;
    private final NotifyRequestConverter notifyRequestConverter;
    private final ProviderDataService providerDataService;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper customObjectMapper;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;

    @Transactional
    public List<EnrichedNotification> getNotificationsForDelivery(int batchSize) {
        return notificationDao.getNotificationsForDelivery(batchSize);
    }

    @Transactional
    @SneakyThrows
    public void process(EnrichedNotification enrichedNotification) {
        var notification = enrichedNotification.getNotification();
        var body = notifyRequestConverter.convert(enrichedNotification);
        var plainTextBody = customObjectMapper.writeValueAsString(body);
        try {
            var forUpdate = checkPending(notification);
            var httpRequest = new HttpPost(forUpdate.getNotificationUrl());
            httpRequest.setEntity(HttpEntities.create(plainTextBody, ContentType.APPLICATION_JSON));
            httpClient.execute(httpRequest, new BasicHttpClientResponseHandler());
            notificationDao.delivered(forUpdate);
        } catch (IOException ex) {
            log.info("IOException when handle NotificationService.process {}", enrichedNotification, ex);
            var forUpdate = checkPending(notification);
            var dispute = enrichedNotification.getDispute();
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            var nextAttemptAfter = exponentialBackOffPollingService.prepareNextPollingInterval(forUpdate, dispute.getCreatedAt(), providerData.getOptions());
            notificationDao.updateNextAttempt(forUpdate, nextAttemptAfter);
            log.debug("Finish IOException handler {}", enrichedNotification, ex);
        } catch (NotificationStatusWasUpdatedByAnotherThreadException ex) {
            log.debug("NotificationStatusWasUpdatedByAnotherThreadException when handle NotificationService.process", ex);
        }
    }

    @SneakyThrows
    public void sendMerchantsNotification(MerchantsNotificationParamsRequest params) {
        var dispute = disputeDao.getByInvoiceId(params.getInvoiceId(), params.getPaymentId());
        var notification = notificationDao.get(dispute.getId());
        var enrichedNotification = getEnrichedNotification(notification, dispute);
        var body = notifyRequestConverter.convert(enrichedNotification);
        var plainTextBody = customObjectMapper.writeValueAsString(body);
        var httpRequest = new HttpPost(notification.getNotificationUrl());
        httpRequest.setEntity(HttpEntities.create(plainTextBody, ContentType.APPLICATION_JSON));
        httpClient.execute(httpRequest, new BasicHttpClientResponseHandler());
    }

    private Notification checkPending(Notification notification) {
        var forUpdate = notificationDao.getSkipLocked(notification.getDisputeId());
        if (forUpdate.getStatus() != NotificationStatus.pending) {
            throw new NotificationStatusWasUpdatedByAnotherThreadException();
        }
        return forUpdate;
    }

    private EnrichedNotification getEnrichedNotification(Notification notification, Dispute dispute) {
        return EnrichedNotification.builder()
                .notification(notification)
                .dispute(dispute)
                .build();
    }
}
