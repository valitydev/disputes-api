package dev.vality.disputes.dao;

import dev.vality.disputes.dao.config.PostgresqlSpringBootITest;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static dev.vality.testcontainers.annotations.util.RandomBeans.random;
import static dev.vality.testcontainers.annotations.util.ValuesGenerator.generateId;
import static dev.vality.testcontainers.annotations.util.ValuesGenerator.generateLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PostgresqlSpringBootITest
public class DisputeDaoTest {

    @Autowired
    private DisputeDao disputeDao;

    @Test
    public void testInsertAndFind() {
        var random = random(Dispute.class);
        disputeDao.save(random);
        assertEquals(random,
                disputeDao.get(random.getId(), random.getInvoiceId(), random.getPaymentId()));
    }

    @Test
    public void testNotFoundException() {
        assertThrows(NotFoundException.class,
                () -> disputeDao.get(generateLong(), generateId(), generateId()));
    }

    @Test
    public void testMultiInsertAndFind() {
        var random = random(Dispute.class);
        random.setId(null);
        random.setInvoiceId("setInvoiceId");
        random.setPaymentId("setPaymentId");
        disputeDao.save(random);
        disputeDao.save(random);
        disputeDao.save(random);
        assertEquals(3,
                disputeDao.get(random.getInvoiceId(), random.getPaymentId()).size());
    }
}
