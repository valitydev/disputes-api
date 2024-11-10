package dev.vality.disputes.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.mapper.RecordRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.vality.disputes.domain.tables.Dispute.DISPUTE;

@Component
@SuppressWarnings({"LineLength"})
public class DisputeDao extends AbstractGenericDao {

    private final RowMapper<Dispute> disputeRowMapper;

    @Autowired
    public DisputeDao(DataSource dataSource) {
        super(dataSource);
        disputeRowMapper = new RecordRowMapper<>(DISPUTE, Dispute.class);
    }

    public UUID save(Dispute dispute) {
        var record = getDslContext().newRecord(DISPUTE, dispute);
        var query = getDslContext().insertInto(DISPUTE)
                .set(record)
                .returning(DISPUTE.ID);
        var keyHolder = new GeneratedKeyHolder();
        execute(query, keyHolder);
        return Optional.ofNullable(keyHolder.getKeyAs(UUID.class)).orElseThrow();
    }

    public List<Dispute> get(String invoiceId, String paymentId) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.INVOICE_ID.eq(invoiceId)
                        .and(DISPUTE.PAYMENT_ID.eq(paymentId)));
        return Optional.ofNullable(fetch(query, disputeRowMapper))
                .orElse(List.of());
    }

    public Dispute get(UUID disputeId) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.ID.eq(disputeId));
        return Optional.ofNullable(fetchOne(query, disputeRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Dispute not found, disputeId='%s'", disputeId), NotFoundException.Type.DISPUTE));
    }

    public Dispute getDisputeForUpdateSkipLocked(UUID disputeId) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.ID.eq(disputeId))
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetchOne(query, disputeRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Dispute not found, disputeId='%s'", disputeId), NotFoundException.Type.DISPUTE));
    }

    public List<Dispute> getDisputesForUpdateSkipLocked(int limit, DisputeStatus disputeStatus) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.STATUS.eq(disputeStatus)
                        .and(DISPUTE.NEXT_CHECK_AFTER.le(LocalDateTime.now(ZoneOffset.UTC))))
                .orderBy(DISPUTE.NEXT_CHECK_AFTER)
                .limit(limit)
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetch(query, disputeRowMapper))
                .orElse(List.of());
    }

    public List<Dispute> getForgottenDisputes() {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.STATUS.ne(DisputeStatus.created)
                        .and(DISPUTE.STATUS.ne(DisputeStatus.pending))
                        .and(DISPUTE.STATUS.ne(DisputeStatus.failed))
                        .and(DISPUTE.STATUS.ne(DisputeStatus.cancelled))
                        .and(DISPUTE.STATUS.ne(DisputeStatus.succeeded)))
                .orderBy(DISPUTE.NEXT_CHECK_AFTER);
        return Optional.ofNullable(fetch(query, disputeRowMapper))
                .orElse(List.of());
    }

    public void setNextStepToCreated(UUID disputeId, LocalDateTime nextCheckAfter) {
        update(disputeId, DisputeStatus.created, nextCheckAfter, null, null, null);
    }

    public void setNextStepToPending(UUID disputeId, LocalDateTime nextCheckAfter) {
        update(disputeId, DisputeStatus.pending, nextCheckAfter, null, null, null);
    }

    public void setNextStepToCreateAdjustment(UUID disputeId, Long changedAmount) {
        update(disputeId, DisputeStatus.create_adjustment, null, null, changedAmount, null);
    }

    public void setNextStepToManualCreated(UUID disputeId, String errorMessage) {
        update(disputeId, DisputeStatus.manual_created, null, errorMessage, null, null);
    }

    public void setNextStepToManualPending(UUID disputeId, String errorMessage) {
        update(disputeId, DisputeStatus.manual_pending, null, errorMessage, null, null);
    }

    public void setNextStepToAlreadyExist(UUID disputeId) {
        update(disputeId, DisputeStatus.already_exist_created, null, null, null, null);
    }

    public void finishSucceeded(UUID disputeId, Long changedAmount) {
        update(disputeId, DisputeStatus.succeeded, null, null, changedAmount, null);
    }

    public void finishFailed(UUID disputeId, String errorMessage) {
        update(disputeId, DisputeStatus.failed, null, errorMessage, null, null);
    }

    public void finishFailedWithMapping(UUID disputeId, String errorMessage, String mapping) {
        update(disputeId, DisputeStatus.failed, null, errorMessage, null, mapping);
    }

    public void finishCancelled(UUID disputeId, String errorMessage, String mapping) {
        update(disputeId, DisputeStatus.cancelled, null, errorMessage, null, mapping);
    }

    private UUID update(UUID disputeId, DisputeStatus status, LocalDateTime nextCheckAfter, String errorMessage, Long changedAmount, String mapping) {
        var set = getDslContext().update(DISPUTE)
                .set(DISPUTE.STATUS, status);
        if (nextCheckAfter != null) {
            set = set.set(DISPUTE.NEXT_CHECK_AFTER, nextCheckAfter);
        }
        if (errorMessage != null) {
            set = set.set(DISPUTE.ERROR_MESSAGE, errorMessage);
        }
        if (mapping != null) {
            set = set.set(DISPUTE.MAPPING, mapping);
        }
        if (changedAmount != null) {
            set = set.set(DISPUTE.CHANGED_AMOUNT, changedAmount);
        }
        var query = set
                .where(DISPUTE.ID.eq(disputeId));
        executeOne(query);
        return disputeId;
    }
}
