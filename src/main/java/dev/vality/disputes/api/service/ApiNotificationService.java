package dev.vality.disputes.api.service;

import dev.vality.adapter.flow.lib.model.PollingInfo;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.dao.NotificationDao;
import dev.vality.disputes.domain.tables.pojos.Notification;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.swag.disputes.model.CreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class ApiNotificationService {

    private final NotificationDao notificationDao;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;

    public void saveNotification(CreateRequest req, PaymentParams paymentParams, PollingInfo pollingInfo, UUID disputeId) {
        if (req.getNotificationUrl() != null) {
            log.debug("Trying to save Notification {}", disputeId);
            var notification = new Notification();
            notification.setDisputeId(disputeId);
            notification.setNotificationUrl(req.getNotificationUrl().getBytes(StandardCharsets.UTF_8));
            notification.setNextAttemptAfter(getNextAttemptAfter(paymentParams, pollingInfo));
            notificationDao.save(notification);
            log.debug("Notification has been saved {}", disputeId);
        }
    }

    private LocalDateTime getNextAttemptAfter(PaymentParams paymentParams, PollingInfo pollingInfo) {
        return exponentialBackOffPollingService.prepareNextPollingInterval(pollingInfo, paymentParams.getOptions());
    }
}
