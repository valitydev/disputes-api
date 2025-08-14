package dev.vality.disputes.admin.management;

import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.util.WiremockUtils;
import dev.vality.provider.payments.PaymentStatusResult;
import dev.vality.provider.payments.ProviderPaymentsServiceSrv;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.*;
import static dev.vality.disputes.util.OpenApiUtil.*;
import static dev.vality.testcontainers.annotations.util.ValuesGenerator.generateId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
public class DebugAdminManagementHandlerTest extends AbstractMockitoConfig {

    @Autowired
    private DebugAdminManagementController debugAdminManagementController;

    @Test
    public void testCancelCreateAdjustment() {
        var disputeId = pendingFlowHandler.handlePending();
        var dispute = disputeDao.get(disputeId);
        debugAdminManagementController.cancelPending(getCancelRequest(dispute.getInvoiceId(), dispute.getPaymentId()));
        assertEquals(DisputeStatus.cancelled, disputeDao.get(disputeId).getStatus());
    }

    @Test
    public void testCancelPending() {
        var disputeId = createdFlowHandler.handleCreate();
        var dispute = disputeDao.get(disputeId);
        debugAdminManagementController.cancelPending(getCancelRequest(dispute.getInvoiceId(), dispute.getPaymentId()));
        assertEquals(DisputeStatus.cancelled, disputeDao.get(disputeId).getStatus());
    }

    @Test
    public void testCancelFailed() {
        var disputeId = pendingFlowHandler.handlePending();
        disputeDao.finishFailed(disputeId, null);
        var dispute = disputeDao.get(disputeId);
        debugAdminManagementController.cancelPending(getCancelRequest(dispute.getInvoiceId(), dispute.getPaymentId()));
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
    }

    @Test
    public void testApproveCreateAdjustmentWithCallHg() {
        var disputeId = pendingFlowHandler.handlePending();
        var dispute = disputeDao.get(disputeId);
        debugAdminManagementController.approvePending(
                getApproveRequest(dispute.getInvoiceId(), dispute.getPaymentId(), false));
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    public void testApproveCreateAdjustmentWithSkipHg() {
        var disputeId = pendingFlowHandler.handlePending();
        var dispute = disputeDao.get(disputeId);
        debugAdminManagementController.approvePending(
                getApproveRequest(dispute.getInvoiceId(), dispute.getPaymentId(), true));
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testApprovePendingWithCallHg() {
        var disputeId = createdFlowHandler.handleCreate();
        var providerPaymentMock = mock(ProviderPaymentsServiceSrv.Client.class);
        when(providerPaymentMock.checkPaymentStatus(any(), any())).thenReturn(new PaymentStatusResult(true));
        when(providerPaymentsThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerPaymentMock);
        var dispute = disputeDao.get(disputeId);
        debugAdminManagementController.approvePending(
                getApproveRequest(dispute.getInvoiceId(), dispute.getPaymentId(), false));
        assertEquals(DisputeStatus.create_adjustment, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    public void testApprovePendingWithSkipHg() {
        var disputeId = createdFlowHandler.handleCreate();
        var dispute = disputeDao.get(disputeId);
        debugAdminManagementController.approvePending(
                getApproveRequest(dispute.getInvoiceId(), dispute.getPaymentId(), true));
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    public void testApproveFailed() {
        var disputeId = pendingFlowHandler.handlePending();
        disputeDao.finishFailed(disputeId, null);
        var dispute = disputeDao.get(disputeId);
        debugAdminManagementController.approvePending(
                getApproveRequest(dispute.getInvoiceId(), dispute.getPaymentId(), true));
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
    }

    @Test
    public void testBindCreatedCreateAdjustment() {
        var disputeId = pendingFlowHandler.handlePending();
        var providerDisputeId = generateId();
        debugAdminManagementController.bindCreated(getBindCreatedRequest(disputeId, providerDisputeId));
        assertEquals(DisputeStatus.create_adjustment, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    public void testBindCreatedPending() {
        var disputeId = createdFlowHandler.handleCreate();
        var providerDisputeId = generateId();
        debugAdminManagementController.bindCreated(getBindCreatedRequest(disputeId, providerDisputeId));
        assertEquals(DisputeStatus.pending, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testBindCreatedAlreadyExist() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
        disputeDao.setNextStepToAlreadyExist(disputeId);
        when(dominantService.getTerminal(any())).thenReturn(createTerminal().get());
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        debugAdminManagementController.bindCreated(getBindCreatedRequest(disputeId, generateId()));
        assertEquals(DisputeStatus.pending, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testGetDispute() {
        WiremockUtils.mockS3AttachmentDownload();
        var disputeId = pendingFlowHandler.handlePending();
        var dispute = disputeDao.get(disputeId);
        var disputes = debugAdminManagementController.getDisputes(
                getGetDisputeRequest(dispute.getInvoiceId(), dispute.getPaymentId(), true));
        assertEquals(1, disputes.getDisputes().size());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testSetPendingForPoolingExpiredDispute() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
        disputeDao.setNextStepToPoolingExpired(disputeId);
        when(dominantService.getTerminal(any())).thenReturn(createTerminal().get());
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        var dispute = disputeDao.get(disputeId);
        debugAdminManagementController.setPendingForPoolingExpired(
                getSetPendingForPoolingExpiredParamsRequest(dispute.getInvoiceId(), dispute.getPaymentId()));
        assertEquals(DisputeStatus.pending, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }
}
