package dev.vality.disputes.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.dao.mapper.NotifyRequestMapper;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.enums.NotificationStatus;
import dev.vality.disputes.domain.tables.pojos.Notification;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.mapper.RecordRowMapper;
import dev.vality.swag.disputes.model.NotifyRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.vality.disputes.domain.tables.Dispute.DISPUTE;
import static dev.vality.disputes.domain.tables.Notification.NOTIFICATION;

@Component
@Slf4j
public class NotificationDao extends AbstractGenericDao {

    private final RowMapper<Notification> notificationRowMapper;
    private final NotifyRequestMapper notifyRequestMapper;

    public NotificationDao(DataSource dataSource) {
        super(dataSource);
        notificationRowMapper = new RecordRowMapper<>(NOTIFICATION, Notification.class);
        notifyRequestMapper = new NotifyRequestMapper();
    }

    public void save(Notification notification) {
        var record = getDslContext().newRecord(NOTIFICATION, notification);
        var query = getDslContext().insertInto(NOTIFICATION)
                .set(record);
        executeOne(query);
    }

    public Notification get(UUID disputeId) {
        var query = getDslContext().selectFrom(NOTIFICATION)
                .where(NOTIFICATION.DISPUTE_ID.eq(disputeId));
        return Optional.ofNullable(fetchOne(query, notificationRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Notification not found, disputeId='%s'", disputeId),
                        NotFoundException.Type.NOTIFICATION));
    }

    public Notification getSkipLocked(UUID disputeId) {
        var query = getDslContext().selectFrom(NOTIFICATION)
                .where(NOTIFICATION.DISPUTE_ID.eq(disputeId))
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetchOne(query, notificationRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Notification not found, disputeId='%s'", disputeId),
                        NotFoundException.Type.NOTIFICATION));
    }

    public List<NotifyRequest> getNotifyRequests(int limit) {
        var query = getDslContext().select(
                        NOTIFICATION.DISPUTE_ID.as("dispute_id"),
                        DISPUTE.INVOICE_ID.as("invoice_id"),
                        DISPUTE.PAYMENT_ID.as("payment_id"),
                        DISPUTE.STATUS.as("dispute_status"),
                        DISPUTE.MAPPING.as("mapping"),
                        DISPUTE.CHANGED_AMOUNT.as("changed_amount")
                ).from(NOTIFICATION)
                .innerJoin(DISPUTE).on(NOTIFICATION.DISPUTE_ID.eq(DISPUTE.ID)
                        .and(DISPUTE.STATUS.in(DisputeStatus.succeeded, DisputeStatus.failed, DisputeStatus.cancelled)))
                .where(NOTIFICATION.NEXT_ATTEMPT_AFTER.le(LocalDateTime.now(ZoneOffset.UTC))
                        .and(NOTIFICATION.STATUS.eq(NotificationStatus.pending)))
                .orderBy(NOTIFICATION.NEXT_ATTEMPT_AFTER)
                .limit(limit);
        return Optional.ofNullable(fetch(query, notifyRequestMapper))
                .orElse(List.of());
    }

    public NotifyRequest getNotifyRequest(UUID disputeId) {
        var query = getDslContext().select(
                        NOTIFICATION.DISPUTE_ID.as("dispute_id"),
                        DISPUTE.INVOICE_ID.as("invoice_id"),
                        DISPUTE.PAYMENT_ID.as("payment_id"),
                        DISPUTE.STATUS.as("dispute_status"),
                        DISPUTE.MAPPING.as("mapping"),
                        DISPUTE.CHANGED_AMOUNT.as("changed_amount")
                ).from(NOTIFICATION)
                .innerJoin(DISPUTE).on(NOTIFICATION.DISPUTE_ID.eq(DISPUTE.ID)
                        .and(DISPUTE.STATUS.in(DisputeStatus.succeeded, DisputeStatus.failed, DisputeStatus.cancelled))
                        .and(DISPUTE.ID.eq(disputeId)));
        return Optional.ofNullable(fetchOne(query, notifyRequestMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Notification not found, disputeId='%s'", disputeId),
                        NotFoundException.Type.NOTIFICATION));
    }

    public void delivered(Notification notification) {
        var query = getDslContext().update(NOTIFICATION)
                .set(NOTIFICATION.STATUS, NotificationStatus.delivered)
                .where(NOTIFICATION.DISPUTE_ID.eq(notification.getDisputeId()));
        executeOne(query);
    }

    public void updateNextAttempt(Notification notification, LocalDateTime nextAttemptAfter) {
        var set = getDslContext().update(NOTIFICATION)
                .set(NOTIFICATION.MAX_ATTEMPTS, NOTIFICATION.MAX_ATTEMPTS.minus(1))
                .set(NOTIFICATION.NEXT_ATTEMPT_AFTER, nextAttemptAfter);
        if (notification.getMaxAttempts() - 1 == 0) {
            set = set.set(NOTIFICATION.STATUS, NotificationStatus.attempts_limit);
        }
        var query = set
                .where(NOTIFICATION.DISPUTE_ID.eq(notification.getDisputeId()));
        executeOne(query);
    }
}
