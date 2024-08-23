package dev.vality.disputes.dao;

import com.zaxxer.hikari.HikariDataSource;
import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.mapper.RecordRowMapper;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static dev.vality.disputes.domain.tables.Dispute.DISPUTE;

@Component
public class DisputeDao extends AbstractGenericDao {

    private final RowMapper<Dispute> disputeRowMapper;

    @Autowired
    public DisputeDao(HikariDataSource dataSource) {
        super(dataSource);
        disputeRowMapper = new RecordRowMapper<>(DISPUTE, Dispute.class);
    }

    public Long save(Dispute dispute) {
        var record = getDslContext().newRecord(DISPUTE, dispute);
        var query = getDslContext().insertInto(DISPUTE)
                .set(record)
                .returning(DISPUTE.ID);
        var keyHolder = new GeneratedKeyHolder();
        execute(query, keyHolder);
        return Optional.ofNullable(keyHolder.getKey())
                .map(Number::longValue)
                .orElseThrow();
    }

    public Dispute get(long disputeId, String invoiceId, String paymentId) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.ID.eq(disputeId)
                        // мы не можем позволить получить несанкционированный доступ к данным, ограничившись disputeId
                        .and(DISPUTE.INVOICE_ID.eq(invoiceId))
                        .and(DISPUTE.PAYMENT_ID.eq(paymentId)));
        return Optional.ofNullable(fetchOne(query, disputeRowMapper))
                .orElseThrow(
                        () -> new NotFoundException(String.format("Dispute not found, disputeId='%s'", disputeId)));
    }

    public List<Dispute> getCreatedDisputesForUpdateSkipLocked(int limit) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.STATUS.eq(DisputeStatus.created))
                .limit(limit)
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetch(query, disputeRowMapper))
                .orElse(List.of());
    }

    public List<Dispute> getPendingDisputesForUpdateSkipLocked(int limit) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.STATUS.eq(DisputeStatus.pending))
                .limit(limit)
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetch(query, disputeRowMapper))
                .orElse(List.of());
    }

    @Nullable
    public Dispute getDisputeForUpdateSkipLocked(long disputeId) {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.ID.eq(disputeId))
                .forUpdate()
                .skipLocked();
        return fetchOne(query, disputeRowMapper);
    }

    public long changeDisputeStatus(long disputeId, DisputeStatus status, String errorMessage, Long changedAmount) {
        var set = getDslContext().update(DISPUTE)
                .set(DISPUTE.STATUS, status);
        if (errorMessage != null) {
            set = set.set(DISPUTE.ERROR_MESSAGE, errorMessage);
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
