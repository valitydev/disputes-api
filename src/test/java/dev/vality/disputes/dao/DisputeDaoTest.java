package dev.vality.disputes.dao;

import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static dev.vality.testcontainers.annotations.util.RandomBeans.random;
import static org.junit.jupiter.api.Assertions.*;

public abstract class DisputeDaoTest {

    @Autowired
    private DisputeDao disputeDao;

    @Test
    public void testInsertAndFind() {
        var random = random(Dispute.class);
        random.setStatus(DisputeStatus.failed);
        disputeDao.save(random);
        assertEquals(random, disputeDao.get(random.getId()));
    }

    @Test
    public void testNotFoundException() {
        assertThrows(NotFoundException.class, () -> disputeDao.get(UUID.randomUUID()));
    }

    @Test
    public void testMultiInsertAndFind() {
        var random = random(Dispute.class);
        random.setId(null);
        random.setInvoiceId("setInvoiceId");
        random.setPaymentId("setPaymentId");
        random.setStatus(DisputeStatus.failed);
        disputeDao.save(random);
        disputeDao.save(random);
        disputeDao.save(random);
        assertEquals(3,
                disputeDao.get(random.getInvoiceId(), random.getPaymentId()).size());
    }

    @Test
    public void testNextCheckAfter() {
        var random = random(Dispute.class);
        random.setStatus(DisputeStatus.already_exist_created);
        var createdAt = LocalDateTime.now(ZoneOffset.UTC);
        random.setCreatedAt(createdAt);
        random.setPollingBefore(createdAt.plusSeconds(10));
        random.setNextCheckAfter(createdAt.plusSeconds(5));
        disputeDao.save(random);
        assertTrue(disputeDao.getDisputesForUpdateSkipLocked(10, random.getStatus()).isEmpty());
        disputeDao.setNextStepToPending(random.getId(), createdAt.plusSeconds(0));
        assertFalse(disputeDao.getDisputesForUpdateSkipLocked(10, DisputeStatus.pending).isEmpty());
        disputeDao.finishFailed(random.getId(), null);
    }
}
