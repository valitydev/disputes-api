package dev.vality.disputes.dao;

import dev.vality.disputes.config.PostgresqlSpringBootITest;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static dev.vality.testcontainers.annotations.util.RandomBeans.random;
import static org.junit.jupiter.api.Assertions.assertEquals;

@PostgresqlSpringBootITest
public class ProviderDisputeDaoTest {

    @Autowired
    private ProviderDisputeDao providerDisputeDao;

    @Test
    public void testInsertAndFind() {
        var random = random(ProviderDispute.class);
        providerDisputeDao.save(random);
        assertEquals(random, providerDisputeDao.get(random.getDisputeId()));
    }
}
