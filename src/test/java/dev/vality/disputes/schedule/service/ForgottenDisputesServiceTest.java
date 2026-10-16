package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.Cash;
import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentPending;
import dev.vality.damsel.domain.InvoicePaymentProcessed;
import dev.vality.damsel.domain.InvoicePaymentRefunded;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.schedule.core.ForgottenDisputesService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.createInvoicePayment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
public class ForgottenDisputesServiceTest extends AbstractMockitoConfig {

    @Autowired
    private ForgottenDisputesService forgottenDisputesService;

    @Test
    @SneakyThrows
    public void testUpdateNextPollingInterval() {
        var disputeId = createForgottenDispute();
        var dispute = disputeDao.get(disputeId);
        when(invoicingClient.getPayment(any(), any())).thenReturn(createInvoicePayment(dispute.getPaymentId()));
        forgottenDisputesService.process(dispute);
        var rescheduled = disputeDao.get(disputeId);
        assertNotEquals(dispute.getNextCheckAfter(), rescheduled.getNextCheckAfter());
        forgottenDisputesService.process(dispute);
        assertEquals(rescheduled.getNextCheckAfter(), disputeDao.get(disputeId).getNextCheckAfter());

        disputeDao.setNextStepToPending(disputeId, dispute.getNextCheckAfter(), dispute.getPollingBefore());
        forgottenDisputesService.process(dispute);
        var pending = disputeDao.get(disputeId);
        assertEquals(DisputeStatus.pending, pending.getStatus());
        assertEquals(dispute.getNextCheckAfter(), pending.getNextCheckAfter());
    }

    @Test
    @SneakyThrows
    public void testFailedWhenInvoicePaymentStatusIsRefunded() {
        var disputeId = createForgottenDispute();
        var dispute = disputeDao.get(disputeId);
        var invoicePayment = createInvoicePayment(dispute.getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.refunded(new InvoicePaymentRefunded()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        forgottenDisputesService.process(dispute);
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
    }

    @Test
    @SneakyThrows
    public void testSuccessWhenInvoicePaymentStatusIsCaptured() {
        var disputeId = createForgottenDispute();
        var dispute = disputeDao.get(disputeId);
        var invoicePayment = createInvoicePayment(dispute.getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        forgottenDisputesService.process(dispute);
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
    }

    @Test
    @SneakyThrows
    public void testWaitWhenInvoicePaymentStatusIsPending() {
        var disputeId = createForgottenDispute();
        var dispute = disputeDao.get(disputeId);
        var invoicePayment = createInvoicePayment(dispute.getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.pending(new InvoicePaymentPending()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        forgottenDisputesService.process(dispute);
        var updatedDispute = disputeDao.get(disputeId);
        assertEquals(DisputeStatus.manual_pending, updatedDispute.getStatus());
        assertTrue(updatedDispute.getNextCheckAfter().isAfter(dispute.getNextCheckAfter()));
    }

    @Test
    @SneakyThrows
    public void testWaitWhenInvoicePaymentStatusIsProcessed() {
        var disputeId = createForgottenDispute();
        var dispute = disputeDao.get(disputeId);
        var invoicePayment = createInvoicePayment(dispute.getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.processed(new InvoicePaymentProcessed()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        forgottenDisputesService.process(dispute);
        var updatedDispute = disputeDao.get(disputeId);
        assertEquals(DisputeStatus.manual_pending, updatedDispute.getStatus());
        assertTrue(updatedDispute.getNextCheckAfter().isAfter(dispute.getNextCheckAfter()));
    }

    @Test
    @SneakyThrows
    public void testSuccessWhenInvoicePaymentStatusIsCapturedWithChangedAmount() {
        var disputeId = createForgottenDispute();
        var dispute = disputeDao.get(disputeId);
        var invoicePayment = createInvoicePayment(dispute.getPaymentId());
        var captured = new InvoicePaymentCaptured()
                .setCost(new Cash()
                        .setAmount(101L)
                        .setCurrency(invoicePayment.getPayment().getCost().getCurrency()));
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(captured));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        forgottenDisputesService.process(dispute);
        var updatedDispute = disputeDao.get(disputeId);
        assertEquals(DisputeStatus.succeeded, updatedDispute.getStatus());
        assertEquals(101L, updatedDispute.getChangedAmount());
    }

    private UUID createForgottenDispute() {
        var disputeId = createdFlowHandler.handleCreate();
        disputeDao.setNextStepToManualPending(disputeId, null, null);
        return disputeId;
    }
}
