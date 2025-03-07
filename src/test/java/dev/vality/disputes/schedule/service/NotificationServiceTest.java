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
        var disputeId = providerCallbackFlowHandler.handleSuccess();
        WiremockUtils.mockNotificationSuccess();
        var dispute = disputeDao.get(disputeId);
        var notification = notificationDao.get(disputeId);
        notificationService.process(EnrichedNotification.builder().dispute(dispute).notification(notification).build());
        Assertions.assertEquals(NotificationStatus.delivered, notificationDao.get(disputeId).getStatus());
    }

    @Test
    @SneakyThrows
    public void testNotificationDeliveredAfterMerchantInternalErrors() {
        var disputeId = providerCallbackFlowHandler.handleSuccess();
        WiremockUtils.mockNotification500();
        var dispute = disputeDao.get(disputeId);
        var notification = notificationDao.get(disputeId);
        notificationService.process(EnrichedNotification.builder().dispute(dispute).notification(notification).build());
        notification = notificationDao.get(disputeId);
        Assertions.assertEquals(NotificationStatus.pending, notification.getStatus());
        Assertions.assertEquals(4, notification.getMaxAttempts());
        notificationService.process(EnrichedNotification.builder().dispute(dispute).notification(notification).build());
        notification = notificationDao.get(disputeId);
        Assertions.assertEquals(NotificationStatus.pending, notification.getStatus());
        Assertions.assertEquals(3, notification.getMaxAttempts());
        WiremockUtils.mockNotificationSuccess();
        notificationService.process(EnrichedNotification.builder().dispute(dispute).notification(notification).build());
        Assertions.assertEquals(NotificationStatus.delivered, notificationDao.get(disputeId).getStatus());
    }
}
