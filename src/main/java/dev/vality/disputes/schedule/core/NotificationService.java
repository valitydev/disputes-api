package dev.vality.disputes.schedule.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.disputes.api.converter.NotifyRequestConverter;
import dev.vality.disputes.dao.NotificationDao;
import dev.vality.disputes.dao.model.EnrichedNotification;
import dev.vality.disputes.domain.enums.NotificationStatus;
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
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class NotificationService {

    private final NotificationDao notificationDao;
    private final NotifyRequestConverter notifyRequestConverter;
    private final ProviderDataService providerDataService;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper customObjectMapper;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;

    @Transactional
    public List<EnrichedNotification> getSkipLocked(int batchSize, int maxAttempt) {
        return notificationDao.getSkipLocked(batchSize, maxAttempt);
    }

    @Transactional
    @SneakyThrows
    public void process(EnrichedNotification enrichedNotification, int maxAttempt) {
        var notification = enrichedNotification.getNotification();
        var body = notifyRequestConverter.convert(enrichedNotification);
        var plainTextBody = customObjectMapper.writeValueAsString(body);
        try {
            checkPending(notification);
            var httpRequest = new HttpPost(getUri(notification));
            httpRequest.setEntity(HttpEntities.create(plainTextBody, ContentType.APPLICATION_JSON));
            httpClient.execute(httpRequest, new BasicHttpClientResponseHandler());
            notificationDao.delivered(notification);
        } catch (IOException e) {
            var dispute = enrichedNotification.getDispute();
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            var nextAttemptAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute, providerData.getOptions());
            notificationDao.updateNextAttempt(notification, nextAttemptAfter, maxAttempt);
        } catch (NotificationStatusWasUpdatedByAnotherThreadException ex) {
            log.debug("NotificationStatusWasUpdatedByAnotherThreadException when handle NotificationService.process", ex);
        }
    }

    public void checkPending(Notification notification) {
        var forUpdate = notificationDao.getSkipLocked(notification.getDisputeId());
        if (forUpdate.getStatus() != NotificationStatus.pending) {
            throw new NotificationStatusWasUpdatedByAnotherThreadException();
        }
    }

    private String getUri(Notification notification) {
        return new String(notification.getNotificationUrl(), StandardCharsets.UTF_8);
    }
}
