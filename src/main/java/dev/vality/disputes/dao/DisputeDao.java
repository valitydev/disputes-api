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

import static dev.vality.disputes.constant.Mapping.APPROVED;
import static dev.vality.disputes.constant.Mapping.SERVER_ERROR;
import static dev.vality.disputes.constant.Mode.AUTOMATIC;
import static dev.vality.disputes.constant.Mode.MANUAL;
import static dev.vality.disputes.domain.tables.Dispute.DISPUTE;

@Component
public class DisputeDao extends AbstractGenericDao {

    private static final String CLEARED = "";

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

    public Dispute get(UUID disputeId) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.ID.eq(disputeId));
        return Optional.ofNullable(fetchOne(query, disputeRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Dispute not found, disputeId='%s'", disputeId), NotFoundException.Type.DISPUTE));
    }

    public Dispute getSkipLocked(UUID disputeId) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.ID.eq(disputeId))
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetchOne(query, disputeRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Dispute not found, disputeId='%s'", disputeId), NotFoundException.Type.DISPUTE));
    }

    public List<Dispute> getSkipLocked(int limit, DisputeStatus disputeStatus) {
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

    public List<Dispute> getForgottenSkipLocked(int limit) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.STATUS.ne(DisputeStatus.created)
                        .and(DISPUTE.STATUS.ne(DisputeStatus.pending))
                        .and(DISPUTE.STATUS.ne(DisputeStatus.failed))
                        .and(DISPUTE.STATUS.ne(DisputeStatus.cancelled))
                        .and(DISPUTE.STATUS.ne(DisputeStatus.succeeded))
                        .and(DISPUTE.NEXT_CHECK_AFTER.le(LocalDateTime.now(ZoneOffset.UTC))))
                .orderBy(DISPUTE.NEXT_CHECK_AFTER)
                .limit(limit)
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetch(query, disputeRowMapper))
                .orElse(List.of());
    }

    public Dispute getByInvoiceId(String invoiceId, String paymentId) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.INVOICE_ID.eq(invoiceId)
                        .and(DISPUTE.PAYMENT_ID.eq(paymentId)))
                .orderBy(DISPUTE.CREATED_AT.desc())
                .limit(1);
        return Optional.ofNullable(fetchOne(query, disputeRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Dispute not found, invoiceId='%s'", invoiceId), NotFoundException.Type.DISPUTE));
    }

    public Dispute getSkipLockedByInvoiceId(String invoiceId, String paymentId) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.INVOICE_ID.eq(invoiceId)
                        .and(DISPUTE.PAYMENT_ID.eq(paymentId)))
                .orderBy(DISPUTE.CREATED_AT.desc())
                .limit(1)
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetchOne(query, disputeRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Dispute not found, invoiceId='%s'", invoiceId), NotFoundException.Type.DISPUTE));
    }

    public void updateNextPollingInterval(Dispute dispute, LocalDateTime nextCheckAfter) {
        update(dispute.getId(), dispute.getStatus(), nextCheckAfter, null, null, null, null, null);
    }

    public void setNextStepToCreated(UUID disputeId, LocalDateTime nextCheckAfter) {
        update(disputeId, DisputeStatus.created, nextCheckAfter, null, null, null, null, AUTOMATIC);
    }

    public void setNextStepToPending(UUID disputeId, LocalDateTime nextCheckAfter) {
        update(disputeId, DisputeStatus.pending, nextCheckAfter, null, null, null, null, AUTOMATIC);
    }

    public void setNextStepToCreateAdjustment(UUID disputeId, Long changedAmount, String providerMessage) {
        update(disputeId, DisputeStatus.create_adjustment, null, changedAmount, APPROVED, providerMessage, CLEARED,
                null);
    }

    public void setNextStepToManualPending(UUID disputeId, String providerMessage, String technicalErrorMessage) {
        update(disputeId, DisputeStatus.manual_pending, null, null, null, providerMessage, technicalErrorMessage,
                MANUAL);
    }

    public void setNextStepToAlreadyExist(UUID disputeId) {
        update(disputeId, DisputeStatus.already_exist_created, null, null, null, null, null, MANUAL);
    }

    public void setNextStepToPoolingExpired(UUID disputeId) {
        update(disputeId, DisputeStatus.pooling_expired, null, null, null, null, null, MANUAL);
    }

    public void finishSucceeded(UUID disputeId, Long changedAmount, String providerMessage) {
        update(disputeId, DisputeStatus.succeeded, null, changedAmount, APPROVED, providerMessage, CLEARED, null);
    }

    public void finishFailed(UUID disputeId, String technicalErrorMessage) {
        update(disputeId, DisputeStatus.failed, null, null, SERVER_ERROR, null, technicalErrorMessage, null);
    }

    public void finishFailedWithMapping(UUID disputeId, String mapping, String providerMessage) {
        update(disputeId, DisputeStatus.failed, null, null, mapping, providerMessage, null, null);
    }

    public void finishCancelled(UUID disputeId, String mapping, String providerMessage) {
        update(disputeId, DisputeStatus.cancelled, null, null, mapping, providerMessage, null, null);
    }

    public void updateProviderMessage(UUID disputeId, String providerMessage) {
        var set = getDslContext().update(DISPUTE)
                .set(DISPUTE.PROVIDER_MSG, providerMessage)
                .where(DISPUTE.ID.eq(disputeId));
        executeOne(set);
    }

    private void update(UUID disputeId, DisputeStatus status, LocalDateTime nextCheckAfter, Long changedAmount,
                        String mapping, String providerMessage, String technicalErrorMessage, String mode) {
        var set = getDslContext().update(DISPUTE)
                .set(DISPUTE.STATUS, status);
        if (nextCheckAfter != null) {
            set = set.set(DISPUTE.NEXT_CHECK_AFTER, nextCheckAfter);
        }
        if (changedAmount != null) {
            set = set.set(DISPUTE.CHANGED_AMOUNT, changedAmount);
        }
        if (mapping != null) {
            set = set.set(DISPUTE.MAPPING, mapping);
        }
        if (providerMessage != null) {
            set = set.set(DISPUTE.PROVIDER_MSG, providerMessage);
        }
        if (technicalErrorMessage != null) {
            set = set.set(DISPUTE.TECH_ERROR_MSG, technicalErrorMessage);
        }
        if (mode != null) {
            set = set.set(DISPUTE.MODE, mode);
        }
        var query = set
                .where(DISPUTE.ID.eq(disputeId));
        executeOne(query);
    }
}
