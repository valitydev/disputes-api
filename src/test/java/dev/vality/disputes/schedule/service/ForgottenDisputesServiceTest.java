package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentRefunded;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.schedule.core.ForgottenDisputesService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static dev.vality.disputes.util.MockUtil.createInvoicePayment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
public class ForgottenDisputesServiceTest extends AbstractMockitoConfig {

    @Autowired
    private ForgottenDisputesService forgottenDisputesService;

    @Test
    @SneakyThrows
    public void testUpdateNextPollingInterval() {
        var disputeId = createdFlowHandler.handleCreate();
        var dispute = disputeDao.get(disputeId);
        when(invoicingClient.getPayment(any(), any())).thenReturn(createInvoicePayment(dispute.getPaymentId()));
        forgottenDisputesService.process(dispute);
        assertNotEquals(dispute.getNextCheckAfter(), disputeDao.get(disputeId).getNextCheckAfter());
    }

    @Test
    @SneakyThrows
    public void testFailedWhenInvoicePaymentStatusIsRefunded() {
        var disputeId = createdFlowHandler.handleCreate();
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
        var disputeId = createdFlowHandler.handleCreate();
        var dispute = disputeDao.get(disputeId);
        var invoicePayment = createInvoicePayment(dispute.getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        forgottenDisputesService.process(dispute);
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
    }
}
