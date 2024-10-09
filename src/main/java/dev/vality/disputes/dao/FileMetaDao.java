package dev.vality.disputes.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import dev.vality.mapper.RecordRowMapper;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static dev.vality.disputes.domain.tables.FileMeta.FILE_META;

@Component
public class FileMetaDao extends AbstractGenericDao {

    private final RowMapper<FileMeta> fileMetaRowMapper;

    @Autowired
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

    @Nullable
    public List<FileMeta> getDisputeFiles(UUID disputeId) {
        var query = getDslContext().selectFrom(FILE_META)
                .where(FILE_META.DISPUTE_ID.eq(disputeId));
        return fetch(query, fileMetaRowMapper);
    }
}
