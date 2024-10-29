package dev.vality.disputes.admin.management;

import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.schedule.service.config.CreatedDisputesTestService;
import dev.vality.disputes.schedule.service.config.DisputeApiTestService;
import dev.vality.disputes.schedule.service.config.PendingDisputesTestService;
import dev.vality.disputes.util.WiremockUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static dev.vality.disputes.util.OpenApiUtil.*;
import static dev.vality.testcontainers.annotations.util.ValuesGenerator.generateId;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WireMockSpringBootITest
@Import({PendingDisputesTestService.class})
public class DebugAdminManagementHandlerTest {

    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private DisputeApiTestService disputeApiTestService;
    @Autowired
    private CreatedDisputesTestService createdDisputesTestService;
    @Autowired
    private PendingDisputesTestService pendingDisputesTestService;
    @Autowired
    private DebugAdminManagementController debugAdminManagementController;

    @Test
    public void testCancelCreateAdjustment() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        debugAdminManagementController.cancelPending(getCancelRequest(disputeId.toString()));
        assertEquals(DisputeStatus.cancelled, disputeDao.get(disputeId).get().getStatus());
    }

    @Test
    public void testCancelPending() {
        var disputeId = createdDisputesTestService.callCreateDisputeRemotely();
        debugAdminManagementController.cancelPending(getCancelRequest(disputeId.toString()));
        assertEquals(DisputeStatus.cancelled, disputeDao.get(disputeId).get().getStatus());
    }

    @Test
    public void testCancelFailed() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        disputeDao.update(disputeId, DisputeStatus.failed);
        debugAdminManagementController.cancelPending(getCancelRequest(disputeId.toString()));
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).get().getStatus());
    }

    @Test
    public void testApproveCreateAdjustmentWithCallHg() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        debugAdminManagementController.approvePending(getApproveRequest(disputeId.toString(), false));
        assertEquals(DisputeStatus.create_adjustment, disputeDao.get(disputeId).get().getStatus());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }

    @Test
    public void testApproveCreateAdjustmentWithSkipHg() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        debugAdminManagementController.approvePending(getApproveRequest(disputeId.toString(), true));
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).get().getStatus());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }

    @Test
    public void testApprovePendingWithSkipHg() {
        var disputeId = createdDisputesTestService.callCreateDisputeRemotely();
        debugAdminManagementController.approvePending(getApproveRequest(disputeId.toString(), true));
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).get().getStatus());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }

    @Test
    public void testApproveFailed() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        disputeDao.update(disputeId, DisputeStatus.failed);
        debugAdminManagementController.approvePending(getApproveRequest(disputeId.toString(), true));
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).get().getStatus());
    }

    @Test
    public void testBindCreatedCreateAdjustment() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        var providerDisputeId = generateId();
        debugAdminManagementController.bindCreated(getBindCreatedRequest(disputeId.toString(), providerDisputeId));
        assertEquals(DisputeStatus.create_adjustment, disputeDao.get(disputeId).get().getStatus());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }

    @Test
    public void testBindCreatedPending() {
        var disputeId = createdDisputesTestService.callCreateDisputeRemotely();
        var providerDisputeId = generateId();
        debugAdminManagementController.bindCreated(getBindCreatedRequest(disputeId.toString(), providerDisputeId));
        assertEquals(DisputeStatus.pending, disputeDao.get(disputeId).get().getStatus());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }

    @Test
    public void testBindCreatedManualCreated() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var providerDisputeId = generateId();
        var disputeId = disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId();
        disputeDao.update(UUID.fromString(disputeId), DisputeStatus.manual_created);
        debugAdminManagementController.bindCreated(getBindCreatedRequest(disputeId, providerDisputeId));
        assertEquals(DisputeStatus.manual_pending, disputeDao.get(UUID.fromString(disputeId)).get().getStatus());
        disputeDao.update(UUID.fromString(disputeId), DisputeStatus.failed);
    }

    @Test
    public void testBindCreatedAlreadyExist() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var providerDisputeId = generateId();
        var disputeId = disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId();
        disputeDao.update(UUID.fromString(disputeId), DisputeStatus.already_exist_created);
        debugAdminManagementController.bindCreated(getBindCreatedRequest(disputeId, providerDisputeId));
        assertEquals(DisputeStatus.pending, disputeDao.get(UUID.fromString(disputeId)).get().getStatus());
        disputeDao.update(UUID.fromString(disputeId), DisputeStatus.failed);
    }

    @Test
    @SneakyThrows
    public void testGetDispute() {
        WiremockUtils.mockS3AttachmentDownload();
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        var disputes = debugAdminManagementController.getDisputes(getGetDisputeRequest(disputeId.toString(), true));
        assertEquals(1, disputes.getDisputes().size());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }
}
