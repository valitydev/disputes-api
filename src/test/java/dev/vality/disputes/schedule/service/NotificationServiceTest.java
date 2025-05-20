package dev.vality.disputes.schedule.service;

import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.dao.NotificationDao;
import dev.vality.disputes.domain.enums.NotificationStatus;
import dev.vality.disputes.schedule.core.NotificationService;
import dev.vality.disputes.util.WiremockUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@WireMockSpringBootITest

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
        var notifyRequest = notificationDao.getNotifyRequest(disputeId);
        notificationService.process(notifyRequest);
        Assertions.assertEquals(NotificationStatus.delivered, notificationDao.get(disputeId).getStatus());
    }

    @Test
    @SneakyThrows
    public void testNotificationDeliveredAfterMerchantInternalErrors() {
        var disputeId = providerCallbackFlowHandler.handleSuccess();
        WiremockUtils.mockNotification500();
        var notifyRequest = notificationDao.getNotifyRequest(disputeId);
        notificationService.process(notifyRequest);
        Assertions.assertEquals(NotificationStatus.pending, notificationDao.get(disputeId).getStatus());
        Assertions.assertEquals(4, notificationDao.get(disputeId).getMaxAttempts());
        notificationService.process(notifyRequest);
        Assertions.assertEquals(NotificationStatus.pending, notificationDao.get(disputeId).getStatus());
        Assertions.assertEquals(3, notificationDao.get(disputeId).getMaxAttempts());
        WiremockUtils.mockNotificationSuccess();
        notificationService.process(notifyRequest);
        Assertions.assertEquals(NotificationStatus.delivered, notificationDao.get(disputeId).getStatus());
    }
}
