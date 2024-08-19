package dev.vality.disputes.dao;

import com.zaxxer.hikari.HikariDataSource;
import dev.vality.dao.DaoException;
import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.mapper.RecordRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static dev.vality.disputes.domain.tables.Dispute.DISPUTE;
import static dev.vality.disputes.domain.tables.FileMeta.FILE_META;
import static dev.vality.disputes.domain.tables.ProviderDispute.PROVIDER_DISPUTE;

@Component
public class DisputeDao extends AbstractGenericDao {

    private final RowMapper<Dispute> disputeRowMapper;
    private final RowMapper<FileMeta> fileMetaRowMapper;
    private final RowMapper<ProviderDispute> providerDisputeRowMapper;

    @Autowired
    public DisputeDao(HikariDataSource dataSource) {
        super(dataSource);
        disputeRowMapper = new RecordRowMapper<>(DISPUTE, Dispute.class);
        fileMetaRowMapper = new RecordRowMapper<>(FILE_META, FileMeta.class);
        providerDisputeRowMapper = new RecordRowMapper<>(PROVIDER_DISPUTE, ProviderDispute.class);
    }

    public Long saveDispute(Dispute dispute) throws DaoException {
        var record = getDslContext().newRecord(DISPUTE, dispute);
        var query = getDslContext().insertInto(DISPUTE)
                .set(record)
                .returning(DISPUTE.ID);
        var keyHolder = new GeneratedKeyHolder();
        execute(query, keyHolder);
        return Optional.ofNullable(keyHolder.getKey()).map(Number::longValue).orElseThrow();
    }

    public Dispute getDispute(long disputeId) throws DaoException {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.ID.eq(disputeId));
        return Optional.ofNullable(fetchOne(query, disputeRowMapper))
                .orElseThrow(
                        () -> new NotFoundException(String.format("Dispute not found, disputeId='%s'", disputeId)));
    }

    public Dispute getDisputeDoUpdate(long disputeId) throws DaoException {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.ID.eq(disputeId))
                .forUpdate();
        return Optional.ofNullable(fetchOne(query, disputeRowMapper))
                .orElseThrow(
                        () -> new NotFoundException(String.format("Dispute not found, disputeId='%s'", disputeId)));
    }

    public Dispute getDisputeDoUpdateSkipLocked(long disputeId) throws DaoException {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.ID.eq(disputeId))
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetchOne(query, disputeRowMapper))
                .orElseThrow(
                        () -> new NotFoundException(String.format("Dispute not found, disputeId='%s'", disputeId)));
    }

    public void changeDisputeStatus(
            long disputeId,
            DisputeStatus status,
            String errorMessage,
            Long changedAmount) throws DaoException {
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
    }

    public List<FileMeta> getDisputeFiles(long disputeId) throws DaoException {
        var query = getDslContext().selectFrom(FILE_META)
                .where(FILE_META.DISPUTE_ID.eq(disputeId));
        return Optional.ofNullable(fetch(query, fileMetaRowMapper))
                .orElseThrow(
                        () -> new NotFoundException(String.format(
                                "Dispute files not found, disputeId='%s'", disputeId)));
    }

    public FileMeta getFile(String fileId) throws DaoException {
        var query = getDslContext().selectFrom(FILE_META)
                .where(FILE_META.FILE_ID.eq(fileId));
        return Optional.ofNullable(fetchOne(query, fileMetaRowMapper))
                .orElseThrow(
                        () -> new NotFoundException(String.format("File not found, disputeId='%s'", fileId)));
    }

    public String attachFile(long disputeId, FileMeta file) throws DaoException {
        file.setDisputeId(disputeId);
        var record = getDslContext().newRecord(FILE_META, file);
        var query = getDslContext().insertInto(FILE_META)
                .set(record);
        executeOne(query);
        return file.getFileId();
    }

    public Long saveProviderDispute(ProviderDispute providerDispute) throws DaoException {
        var record = getDslContext().newRecord(PROVIDER_DISPUTE, providerDispute);
        var query = getDslContext().insertInto(PROVIDER_DISPUTE)
                .set(record);
        executeOne(query);
        return providerDispute.getDisputeId();
    }

    public ProviderDispute getProviderDispute(long disputeId) throws DaoException {
        var query = getDslContext().selectFrom(PROVIDER_DISPUTE)
                .where(PROVIDER_DISPUTE.DISPUTE_ID.eq(disputeId));
        return Optional.ofNullable(fetchOne(query, providerDisputeRowMapper))
                .orElseThrow(
                        () -> new NotFoundException(String.format("File not found, disputeId='%s'", disputeId)));
    }

    public List<Dispute> getPendingDisputes(int limit) throws DaoException {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.STATUS.eq(DisputeStatus.pending))
                .limit(limit)
                .forUpdate()
                .skipLocked();
        return fetch(query, disputeRowMapper);
    }

    public List<Dispute> getCreatedDisputes(int limit) throws DaoException {
        var query = getDslContext().selectFrom(DISPUTE)
                .where(DISPUTE.STATUS.eq(DisputeStatus.created))
                .limit(limit)
                .forUpdate()
                .skipLocked();
        return fetch(query, disputeRowMapper);
    }
}
