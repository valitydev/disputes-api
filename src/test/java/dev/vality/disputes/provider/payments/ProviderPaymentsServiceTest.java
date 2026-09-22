package dev.vality.disputes.provider.payments;

import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentPending;
import dev.vality.damsel.domain.InvoicePaymentProcessed;
import dev.vality.damsel.domain.InvoicePaymentRefunded;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.provider.payments.PaymentStatusResult;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static dev.vality.disputes.util.MockUtil.createInvoicePayment;
import static dev.vality.disputes.util.MockUtil.getTransactionInfoInvoicePaymentAdjustment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
@TestPropertySource(properties = {
        "server.port=${local.server.port}",
        "provider.payments.isProviderCallbackEnabled=true",
})
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

    @Test
    @SneakyThrows
    public void testRetryLaterWhenInvoicePaymentStatusIsPending() {
        var disputeId = pendingFlowHandler.handlePending();
        var dispute = disputeDao.get(disputeId);
        var providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        var invoicePayment = createInvoicePayment(providerCallback.getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.pending(new InvoicePaymentPending()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);

        providerPaymentsService.callHgForCreateAdjustment(providerCallback);

        var nextCheckAfter = providerCallback.getNextCheckAfter();
        providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        assertEquals(ProviderPaymentsStatus.create_adjustment, providerCallback.getStatus());
        assertTrue(providerCallback.getNextCheckAfter().isAfter(nextCheckAfter));
        assertEquals(DisputeStatus.create_adjustment, disputeDao.get(disputeId).getStatus());
        verify(invoicingClient, never()).createPaymentAdjustment(any(), any(), any());
    }

    @Test
    @SneakyThrows
    public void testRetryLaterWhenInvoicePaymentStatusIsProcessed() {
        var disputeId = pendingFlowHandler.handlePending();
        var dispute = disputeDao.get(disputeId);
        var providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        var invoicePayment = createInvoicePayment(providerCallback.getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.processed(new InvoicePaymentProcessed()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);

        providerPaymentsService.callHgForCreateAdjustment(providerCallback);

        var nextCheckAfter = providerCallback.getNextCheckAfter();
        providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        assertEquals(ProviderPaymentsStatus.create_adjustment, providerCallback.getStatus());
        assertTrue(providerCallback.getNextCheckAfter().isAfter(nextCheckAfter));
        assertEquals(DisputeStatus.create_adjustment, disputeDao.get(disputeId).getStatus());
        verify(invoicingClient, never()).createPaymentAdjustment(any(), any(), any());
    }

    @Test
    @SneakyThrows
    public void testSuccessWhenInvoicePaymentStatusIsCapturedWithChangedAmountAndTransactionInfo() {
        var transactionInfo = new TransactionInfo("new-trx-id", Map.of("rrn", "123"));
        var disputeId = pendingFlowHandler.handlePending(
                new PaymentStatusResult(true).setChangedTransactionInfo(transactionInfo));
        var dispute = disputeDao.get(disputeId);
        var providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        assertNotNull(providerCallback.getTransactionInfo());
        providerCallback.setChangedAmount(101L);
        providerCallbackDao.update(providerCallback);
        var invoicePayment = createInvoicePayment(providerCallback.getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);

        providerPaymentsService.callHgForCreateAdjustment(providerCallback);

        providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        assertEquals(ProviderPaymentsStatus.succeeded, providerCallback.getStatus());
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
        verify(invoicingClient, never()).createPaymentAdjustment(any(), any(), any());
    }

    @Test
    @SneakyThrows
    public void testCreateTransactionInfoAdjustment() {
        var transactionInfo = new TransactionInfo("new-trx-id", Map.of("rrn", "123"));
        var disputeId = pendingFlowHandler.handlePending(
                new PaymentStatusResult(true).setChangedTransactionInfo(transactionInfo));
        var dispute = disputeDao.get(disputeId);
        var providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        assertNotNull(providerCallback.getTransactionInfo());
        var invoicePayment = createInvoicePayment(providerCallback.getPaymentId());
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);

        providerPaymentsService.callHgForCreateAdjustment(providerCallback);

        var reason = providerPaymentsAdjustmentExtractor.getReason(providerCallback);
        invoicePayment.setAdjustments(List.of(
                getTransactionInfoInvoicePaymentAdjustment("transaction-info-adjustment", reason, transactionInfo)));
        providerPaymentsService.callHgForCreateAdjustment(providerCallback);

        var paramsCaptor = ArgumentCaptor.forClass(InvoicePaymentAdjustmentParams.class);
        verify(invoicingClient, times(2)).createPaymentAdjustment(any(), any(), paramsCaptor.capture());
        var scenarios = paramsCaptor.getAllValues().stream()
                .map(InvoicePaymentAdjustmentParams::getScenario)
                .toList();
        assertTrue(scenarios.get(0).isSetTransactionInfo());
        assertEquals(transactionInfo, scenarios.get(0).getTransactionInfo().getTrx());
        assertTrue(scenarios.get(1).isSetStatusChange());
        assertEquals(ProviderPaymentsStatus.succeeded,
                providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId()).getStatus());
    }

    @Test
    @SneakyThrows
    public void testCreateTransactionInfoAdjustmentForUnsuccessfulPaymentStatus() {
        var transactionInfo = new TransactionInfo("new-trx-id", Map.of("rrn", "123"));
        var disputeId = pendingFlowHandler.handlePending(
                new PaymentStatusResult(false).setChangedTransactionInfo(transactionInfo));
        var dispute = disputeDao.get(disputeId);
        var providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        assertNotNull(providerCallback.getTransactionInfo());
        var invoicePayment = createInvoicePayment(providerCallback.getPaymentId());
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);

        providerPaymentsService.callHgForCreateAdjustment(providerCallback);

        var reason = providerPaymentsAdjustmentExtractor.getReason(providerCallback);
        invoicePayment.setAdjustments(List.of(
                getTransactionInfoInvoicePaymentAdjustment("transaction-info-adjustment", reason, transactionInfo)));
        providerPaymentsService.callHgForCreateAdjustment(providerCallback);

        var paramsCaptor = ArgumentCaptor.forClass(InvoicePaymentAdjustmentParams.class);
        verify(invoicingClient).createPaymentAdjustment(any(), any(), paramsCaptor.capture());
        assertTrue(paramsCaptor.getValue().getScenario().isSetTransactionInfo());
        assertEquals(ProviderPaymentsStatus.failed,
                providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId()).getStatus());
    }
}
