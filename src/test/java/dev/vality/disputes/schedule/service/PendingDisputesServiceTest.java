package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentRefunded;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.util.MockUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static dev.vality.disputes.constant.ModerationPrefix.DISPUTES_UNKNOWN_MAPPING;
import static dev.vality.disputes.util.MockUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
public class PendingDisputesServiceTest extends AbstractMockitoConfig {

    @Test
    public void testDisputeStatusSuccessResult() {
        var disputeId = pendingFlowHandler.handlePending();
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testDisputeStatusPendingResult() {
        var disputeId = createdFlowHandler.handleCreate();
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.checkDisputeStatus(any())).thenReturn(createDisputeStatusPendingResult());
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        var dispute = disputeDao.get(disputeId);
        pendingDisputesService.callPendingDisputeRemotely(dispute);
        assertEquals(DisputeStatus.pending, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testProviderDisputeNotFound() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
        disputeDao.setNextStepToPending(disputeId, null);
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        var dispute = disputeDao.get(disputeId);
        pendingDisputesService.callPendingDisputeRemotely(dispute);
        assertEquals(DisputeStatus.created, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testDisputeStatusFailResult() {
        var disputeId = createdFlowHandler.handleCreate();
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.checkDisputeStatus(any())).thenReturn(createDisputeStatusFailResult());
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        var dispute = disputeDao.get(disputeId);
        pendingDisputesService.callPendingDisputeRemotely(dispute);
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
    }

    @Test
    @SneakyThrows
    public void testManualPendingWhenStatusFailResultWithDisputesUnknownMapping() {
        var disputeId = createdFlowHandler.handleCreate();
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        var disputeStatusFailResult = createDisputeStatusFailResult();
        disputeStatusFailResult.getStatusFail().getFailure().setCode(DISPUTES_UNKNOWN_MAPPING);
        when(providerMock.checkDisputeStatus(any())).thenReturn(disputeStatusFailResult);
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        var dispute = disputeDao.get(disputeId);
        pendingDisputesService.callPendingDisputeRemotely(dispute);
        assertEquals(DisputeStatus.manual_pending, disputeDao.get(disputeId).getStatus());
        assertTrue(disputeDao.get(disputeId).getProviderMsg().contains("code ="));
        assertTrue(disputeDao.get(disputeId).getProviderMsg().contains("description ="));
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testManualPendingWhenUnexpectedResultMapping() {
        var disputeId = createdFlowHandler.handleCreate();
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.checkDisputeStatus(any())).thenThrow(getUnexpectedResultWException());
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        var dispute = disputeDao.get(disputeId);
        pendingDisputesService.callPendingDisputeRemotely(dispute);
        assertEquals(DisputeStatus.manual_pending, disputeDao.get(disputeId).getStatus());
        assertTrue(disputeDao.get(disputeId).getTechErrorMsg().contains("Unexpected result"));
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testManualPendingWhenUnexpectedResultBase64Mapping() {
        var disputeId = createdFlowHandler.handleCreate();
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.checkDisputeStatus(any())).thenThrow(getUnexpectedResultBase64WException());
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        var dispute = disputeDao.get(disputeId);
        pendingDisputesService.callPendingDisputeRemotely(dispute);
        assertEquals(DisputeStatus.manual_pending, disputeDao.get(disputeId).getStatus());
        assertTrue(disputeDao.get(disputeId).getTechErrorMsg().contains("Unexpected result"));
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testFailedWhenInvoicePaymentStatusIsRefunded() {
        var disputeId = createdFlowHandler.handleCreate();
        var dispute = disputeDao.get(disputeId);
        var invoicePayment = createInvoicePayment(dispute.getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.refunded(new InvoicePaymentRefunded()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        pendingDisputesService.callPendingDisputeRemotely(dispute);
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
        pendingDisputesService.callPendingDisputeRemotely(dispute);
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
    }
}
