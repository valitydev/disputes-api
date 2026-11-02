package dev.vality.disputes.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.tables.pojos.AttachmentDocument;
import dev.vality.mapper.RecordRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static dev.vality.disputes.domain.enums.AttachmentDocumentStatus.pending;
import static dev.vality.disputes.domain.tables.AttachmentDocument.ATTACHMENT_DOCUMENT;

@Component
public class AttachmentDocumentDao extends AbstractGenericDao {

    private final RowMapper<AttachmentDocument> rowMapper =
            new RecordRowMapper<>(ATTACHMENT_DOCUMENT, AttachmentDocument.class);

    public AttachmentDocumentDao(DataSource dataSource) {
        super(dataSource);
    }

    public void save(AttachmentDocument document) {
        executeOne(getDslContext().insertInto(ATTACHMENT_DOCUMENT)
                .set(getDslContext().newRecord(ATTACHMENT_DOCUMENT, document)));
    }

    public List<String> findPending(int limit) {
        return fetch(getDslContext().select(ATTACHMENT_DOCUMENT.FILE_ID).from(ATTACHMENT_DOCUMENT)
                .where(ATTACHMENT_DOCUMENT.STATUS.eq(pending))
                .and(ATTACHMENT_DOCUMENT.NEXT_ATTEMPT_AFTER.le(LocalDateTime.now(ZoneOffset.UTC)))
                .orderBy(ATTACHMENT_DOCUMENT.NEXT_ATTEMPT_AFTER, ATTACHMENT_DOCUMENT.FILE_ID)
                .limit(limit), (rs, rowNum) -> rs.getString("file_id"));
    }

    public Optional<AttachmentDocument> getPendingSkipLocked(String fileId) {
        return Optional.ofNullable(fetchOne(getDslContext().selectFrom(ATTACHMENT_DOCUMENT)
                .where(ATTACHMENT_DOCUMENT.FILE_ID.eq(fileId))
                .and(ATTACHMENT_DOCUMENT.STATUS.eq(pending))
                .and(ATTACHMENT_DOCUMENT.NEXT_ATTEMPT_AFTER.le(LocalDateTime.now(ZoneOffset.UTC)))
                .forUpdate().skipLocked(), rowMapper));
    }

    public void update(AttachmentDocument document) {
        executeOne(getDslContext().update(ATTACHMENT_DOCUMENT)
                .set(getDslContext().newRecord(ATTACHMENT_DOCUMENT, document))
                .where(ATTACHMENT_DOCUMENT.FILE_ID.eq(document.getFileId()))
                .and(ATTACHMENT_DOCUMENT.STATUS.eq(pending)));
    }
}
