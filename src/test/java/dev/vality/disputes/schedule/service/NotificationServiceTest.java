package dev.vality.disputes.schedule.service;

import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.dao.NotificationDao;
import dev.vality.disputes.dao.model.EnrichedNotification;
import dev.vality.disputes.domain.enums.NotificationStatus;
import dev.vality.disputes.schedule.core.NotificationService;
import dev.vality.disputes.util.WiremockUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@WireMockSpringBootITest
@SuppressWarnings({"LineLength"})
public class NotificationServiceTest extends AbstractMockitoConfig {

    @Autowired
    private NotificationDao notificationDao;
    @Autowired
    private NotificationService notificationService;

    @Test
    @SneakyThrows
    public void testNotificationDelivered() {
        var disputeId = pendingFlowHnadler.handlePending();
        WiremockUtils.mockNotificationSuccess();
        // todo providercallback flow set success
        disputeDao.finishSucceeded(disputeId, null);
        var dispute = disputeDao.get(disputeId);
        var notification = notificationDao.get(disputeId);
        notificationService.process(EnrichedNotification.builder().dispute(dispute).notification(notification).build(), 5);
        Assertions.assertEquals(NotificationStatus.delivered, notificationDao.get(disputeId).getStatus());
    }

    @Test
    @SneakyThrows
    public void testNotificationDeliveredAfterMerchantInternalErrors() {
        var disputeId = pendingFlowHnadler.handlePending();
        WiremockUtils.mockNotification500();
        // todo providercallback flow set success
        disputeDao.finishSucceeded(disputeId, null);
        var dispute = disputeDao.get(disputeId);
        var notification = notificationDao.get(disputeId);
        notificationService.process(EnrichedNotification.builder().dispute(dispute).notification(notification).build(), 5);
        notification = notificationDao.get(disputeId);
        Assertions.assertEquals(NotificationStatus.pending, notification.getStatus());
        Assertions.assertEquals(1, notification.getAttempt());
        notificationService.process(EnrichedNotification.builder().dispute(dispute).notification(notification).build(), 5);
        notification = notificationDao.get(disputeId);
        Assertions.assertEquals(NotificationStatus.pending, notification.getStatus());
        Assertions.assertEquals(2, notification.getAttempt());
        WiremockUtils.mockNotificationSuccess();
        notificationService.process(EnrichedNotification.builder().dispute(dispute).notification(notification).build(), 5);
        Assertions.assertEquals(NotificationStatus.delivered, notificationDao.get(disputeId).getStatus());
    }
}
