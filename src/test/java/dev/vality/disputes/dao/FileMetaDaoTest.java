package dev.vality.disputes.dao;

import dev.vality.disputes.config.EmbeddedPostgresWithFlywaySpringBootITest;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static dev.vality.testcontainers.annotations.util.RandomBeans.random;
import static dev.vality.testcontainers.annotations.util.ValuesGenerator.generateId;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EmbeddedPostgresWithFlywaySpringBootITest
//@PostgresqlSpringBootITest
public class FileMetaDaoTest {

    @Autowired
    private FileMetaDao fileMetaDao;

    @Test
    public void testInsertAndFind() {
        var random = random(FileMeta.class);
        random.setFileId(generateId());
        random.setDisputeId(1L);
        fileMetaDao.save(random);
        random.setFileId(generateId());
        fileMetaDao.save(random);
        assertEquals(2, fileMetaDao.getDisputeFiles(1L).size());
    }
}
