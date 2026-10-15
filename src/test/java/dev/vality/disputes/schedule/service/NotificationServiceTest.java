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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

@WireMockSpringBootITest
public class NotificationServiceTest extends AbstractMockitoConfig {

    @Autowired
    private NotificationDao notificationDao;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @SneakyThrows
    public void testNotificationDelivered() {
        var disputeId = providerCallbackFlowHandler.handleSuccess();
        makeNotificationDue(disputeId);
        WiremockUtils.mockNotificationSuccess();
        var notifyRequest = notificationDao.getNotifyRequest(disputeId);
        notificationService.process(notifyRequest);
        Assertions.assertEquals(NotificationStatus.delivered, notificationDao.get(disputeId).getStatus());
    }

    @Test
    @SneakyThrows
    public void testNotificationDeliveredAfterMerchantInternalErrors() {
        var disputeId = providerCallbackFlowHandler.handleSuccess();
        makeNotificationDue(disputeId);
        WiremockUtils.mockNotification500();
        var notifyRequest = notificationDao.getNotifyRequest(disputeId);
        notificationService.process(notifyRequest);
        Assertions.assertEquals(NotificationStatus.pending, notificationDao.get(disputeId).getStatus());
        Assertions.assertEquals(4, notificationDao.get(disputeId).getMaxAttempts());
        notificationService.process(notifyRequest);
        Assertions.assertEquals(NotificationStatus.pending, notificationDao.get(disputeId).getStatus());
        Assertions.assertEquals(4, notificationDao.get(disputeId).getMaxAttempts());
        makeNotificationDue(disputeId);
        WiremockUtils.mockNotificationSuccess();
        notificationService.process(notifyRequest);
        Assertions.assertEquals(NotificationStatus.delivered, notificationDao.get(disputeId).getStatus());
    }

    private void makeNotificationDue(UUID disputeId) {
        jdbcTemplate.update("UPDATE dspt.notification SET next_attempt_after = " +
                "(now() AT TIME ZONE 'UTC') - INTERVAL '1 second' WHERE dispute_id = ?", disputeId);
    }
}
