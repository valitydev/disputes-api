package dev.vality.disputes.dao;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.testcontainers.annotations.DefaultSpringBootTest;
import dev.vality.testcontainers.annotations.postgresql.PostgresqlTestcontainerSingleton;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static dev.vality.testcontainers.annotations.util.RandomBeans.random;
import static org.junit.jupiter.api.Assertions.assertEquals;

@PostgresqlTestcontainerSingleton
@DefaultSpringBootTest
public class DisputeDaoTest {

    @Autowired
    private DisputeDao disputeDao;

    @Test
    public void insertAndFindAdjustmentEventTest() {
        var random = random(Dispute.class);
        disputeDao.save(random);
        assertEquals(random,
                disputeDao.get(random.getId(), random.getInvoiceId(), random.getPaymentId()));
    }
}
