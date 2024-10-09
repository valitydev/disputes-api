package dev.vality.disputes.dao;

import dev.vality.disputes.config.PostgresqlSpringBootITest;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static dev.vality.testcontainers.annotations.util.RandomBeans.random;
import static dev.vality.testcontainers.annotations.util.ValuesGenerator.generateId;
import static org.junit.jupiter.api.Assertions.assertEquals;

@PostgresqlSpringBootITest
public class FileMetaDaoTest {

    @Autowired
    private FileMetaDao fileMetaDao;

    @Test
    public void testInsertAndFind() {
        var disputeId = UUID.fromString("bfdf1dfc-cf66-4d8d-bc34-4d987b3f7351");
        var random = random(FileMeta.class);
        random.setFileId(generateId());
        random.setDisputeId(disputeId);
        fileMetaDao.save(random);
        random.setFileId(generateId());
        fileMetaDao.save(random);
        assertEquals(2, fileMetaDao.getDisputeFiles(disputeId).size());
    }
}
