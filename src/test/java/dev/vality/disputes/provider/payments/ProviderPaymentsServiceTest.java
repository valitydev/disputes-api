package dev.vality.disputes.provider.payments;

import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentRefunded;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static dev.vality.disputes.util.MockUtil.createInvoicePayment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
@TestPropertySource(properties = {
        "server.port=${local.server.port}",
        "provider.payments.isProviderCallbackEnabled=true",
})
@SuppressWarnings({"VariableDeclarationUsageDistance", "LineLength"})
public class ProviderPaymentsServiceTest extends AbstractMockitoConfig {

    @Test
    @SneakyThrows
    public void testProviderPaymentsSuccessResult() {
        var disputeId = providerCallbackFlowHandler.handleSuccess();
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testFailedWhenInvoicePaymentStatusIsRefunded() {
        var disputeId = pendingFlowHandler.handlePending();
        var dispute = disputeDao.get(disputeId);
        var providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        var invoicePayment = createInvoicePayment(providerCallback.getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.refunded(new InvoicePaymentRefunded()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        providerPaymentsService.callHgForCreateAdjustment(providerCallback);
        providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        assertEquals(ProviderPaymentsStatus.failed, providerCallback.getStatus());
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
    }

    @Test
    @SneakyThrows
    public void testSuccessWhenInvoicePaymentStatusIsCaptured() {
        var disputeId = pendingFlowHandler.handlePending();
        var dispute = disputeDao.get(disputeId);
        var providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        var invoicePayment = createInvoicePayment(providerCallback.getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        providerPaymentsService.callHgForCreateAdjustment(providerCallback);
        providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        assertEquals(ProviderPaymentsStatus.succeeded, providerCallback.getStatus());
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
    }
}
