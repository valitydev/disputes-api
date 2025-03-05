package dev.vality.disputes.dao;

import dev.vality.disputes.config.PostgresqlSpringBootITest;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.enums.NotificationStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static dev.vality.testcontainers.annotations.util.RandomBeans.random;
import static org.junit.jupiter.api.Assertions.assertEquals;

@PostgresqlSpringBootITest
@SuppressWarnings({"LineLength"})
public class NotificationDaoTest {

    @Autowired
    private NotificationDao notificationDao;
    @Autowired
    private DisputeDao disputeDao;

    @Test
    public void testInsertAndFind() {
        var random = random(Notification.class);
        notificationDao.save(random);
        assertEquals(random, notificationDao.get(random.getDisputeId()));
    }

    @Test
    public void testMultiInsertAndFindActual() {
        var createdAt = LocalDateTime.now(ZoneOffset.UTC);
        // only this is valid
        notificationDao.save(getNotification(NotificationStatus.pending, createdAt, getDispute().getId()));
        notificationDao.save(getNotification(NotificationStatus.delivered, createdAt, getDispute().getId()));
        notificationDao.save(getNotification(NotificationStatus.attempts_limit, createdAt, getDispute().getId()));
        notificationDao.save(getNotification(NotificationStatus.pending, createdAt.plusSeconds(10), getDispute().getId()));
        notificationDao.save(getNotification(NotificationStatus.pending, createdAt, UUID.randomUUID()));
        notificationDao.save(getNotification(NotificationStatus.pending, createdAt, UUID.randomUUID()));
        var notification = getNotification(NotificationStatus.pending, createdAt, getDispute().getId());
        var maxAttempt = 5;
        notification.setAttempt(maxAttempt);
        notificationDao.save(notification);
        var enrichedNotifications = notificationDao.getNotificationsForDelivery(10, maxAttempt);
        assertEquals(1, enrichedNotifications.size());
    }

    @Test
    public void testDelivered() {
        var createdAt = LocalDateTime.now(ZoneOffset.UTC);
        var notification = getNotification(NotificationStatus.pending, createdAt, getDispute().getId());
        notificationDao.save(notification);
        notificationDao.delivered(notification);
        assertEquals(NotificationStatus.delivered, notificationDao.get(notification.getDisputeId()).getStatus());
    }

    @Test
    public void testAttemptsLimit() {
        var maxAttempt = 5;
        var createdAt = LocalDateTime.now(ZoneOffset.UTC);
        var notification = getNotification(NotificationStatus.pending, createdAt, getDispute().getId());
        notificationDao.save(notification);
        notification = notificationDao.get(notification.getDisputeId());
        notificationDao.updateNextAttempt(notification, createdAt, maxAttempt);
        assertEquals(1, notificationDao.get(notification.getDisputeId()).getAttempt());
        notificationDao.updateNextAttempt(notification, createdAt, maxAttempt);
        assertEquals(2, notificationDao.get(notification.getDisputeId()).getAttempt());
        notificationDao.updateNextAttempt(notification, createdAt, 0);
        assertEquals(NotificationStatus.attempts_limit, notificationDao.get(notification.getDisputeId()).getStatus());
    }

    private Dispute getDispute() {
        var dispute = random(Dispute.class);
        dispute.setStatus(DisputeStatus.failed);
        disputeDao.save(dispute);
        return dispute;
    }

    private Notification getNotification(NotificationStatus status, LocalDateTime nextAttemptAfter, UUID disputeId) {
        var random = random(Notification.class);
        random.setStatus(status);
        random.setNextAttemptAfter(nextAttemptAfter);
        random.setDisputeId(disputeId);
        random.setAttempt(null);
        return random;
    }
}
