package dev.vality.disputes.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.dao.mapper.EnrichedNotificationMapper;
import dev.vality.disputes.dao.model.EnrichedNotification;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.enums.NotificationStatus;
import dev.vality.disputes.domain.tables.pojos.Notification;
import dev.vality.mapper.RecordRowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static dev.vality.disputes.domain.tables.Dispute.DISPUTE;
import static dev.vality.disputes.domain.tables.Notification.NOTIFICATION;

@Component
@Slf4j
public class NotificationDao extends AbstractGenericDao {

    private final RowMapper<Notification> notificationRowMapper;
    private final EnrichedNotificationMapper enrichedNotificationMapper;

    @Autowired
    public NotificationDao(DataSource dataSource) {
        super(dataSource);
        notificationRowMapper = new RecordRowMapper<>(NOTIFICATION, Notification.class);
        enrichedNotificationMapper = new EnrichedNotificationMapper();
    }

    public void save(Notification notification) {
        var record = getDslContext().newRecord(NOTIFICATION, notification);
        var query = getDslContext().insertInto(NOTIFICATION)
                .set(record);
        executeOne(query);
    }
//
//    public Notification get(UUID disputeId) {
//        var query = getDslContext().selectFrom(NOTIFICATION)
//                .where(NOTIFICATION.DISPUTE_ID.eq(disputeId));
//        return Optional.ofNullable(fetchOne(query, notificationRowMapper))
//                .orElseThrow(() -> new NotFoundException(
//                        String.format("Notification not found, disputeId='%s'", disputeId), Type.NOTIFICATION));
//    }

    public List<EnrichedNotification> getSkipLocked(int limit, int maxAttempt) {
        var query = getDslContext().select().from(NOTIFICATION)
                .leftJoin(DISPUTE).on(NOTIFICATION.DISPUTE_ID.eq(DISPUTE.ID)
                        .and(DISPUTE.STATUS.eq(DisputeStatus.succeeded)
                                .or(DISPUTE.STATUS.eq(DisputeStatus.failed))
                                .or(DISPUTE.STATUS.eq(DisputeStatus.cancelled))))
                .where(NOTIFICATION.NEXT_ATTEMPT_AFTER.le(LocalDateTime.now(ZoneOffset.UTC))
                        .and(NOTIFICATION.ATTEMPT.lessThan(maxAttempt))
                        .and(NOTIFICATION.STATUS.eq(NotificationStatus.pending)))
                .orderBy(NOTIFICATION.NEXT_ATTEMPT_AFTER)
                .limit(limit)
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetch(query, enrichedNotificationMapper))
                .orElse(List.of());
    }

    public void delivered(Notification notification) {
        var query = getDslContext().update(NOTIFICATION)
                .set(NOTIFICATION.STATUS, NotificationStatus.delivered)
                .where(NOTIFICATION.DISPUTE_ID.eq(notification.getDisputeId()));
        executeOne(query);
    }

    public void updateNextAttempt(Notification notification, LocalDateTime nextAttemptAfter, int maxAttempt) {
        var set = getDslContext().update(NOTIFICATION)
                .set(NOTIFICATION.ATTEMPT, NOTIFICATION.ATTEMPT.plus(1))
                .set(NOTIFICATION.NEXT_ATTEMPT_AFTER, nextAttemptAfter);
        if (notification.getAttempt() >= maxAttempt) {
            set = set.set(NOTIFICATION.STATUS, NotificationStatus.attempts_limit);
        }
        var query = set
                .where(NOTIFICATION.DISPUTE_ID.eq(notification.getDisputeId()));
        executeOne(query);
    }
}
