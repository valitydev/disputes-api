package dev.vality.disputes.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.exception.NotFoundException.Type;
import dev.vality.mapper.RecordRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.vality.disputes.domain.tables.FileMeta.FILE_META;

@Component
public class FileMetaDao extends AbstractGenericDao {

    private final RowMapper<FileMeta> fileMetaRowMapper;

    public FileMetaDao(DataSource dataSource) {
        super(dataSource);
        fileMetaRowMapper = new RecordRowMapper<>(FILE_META, FileMeta.class);
    }

    public String save(FileMeta file) {
        var record = getDslContext().newRecord(FILE_META, file);
        var query = getDslContext().insertInto(FILE_META)
                .set(record);
        executeOne(query);
        return file.getFileId();
    }

    public List<FileMeta> getDisputeFiles(UUID disputeId) {
        var query = getDslContext().selectFrom(FILE_META)
                .where(FILE_META.DISPUTE_ID.eq(disputeId));
        return Optional.ofNullable(fetch(query, fileMetaRowMapper))
                .filter(fileMetas -> !fileMetas.isEmpty())
                .orElseThrow(() -> new NotFoundException(
                        String.format("FileMeta not found, disputeId='%s'", disputeId), Type.FILEMETA));

    }
}
