package dev.vality.disputes.manualparsing;

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

import static dev.vality.disputes.util.OpenApiUtil.*;
import static dev.vality.testcontainers.annotations.util.ValuesGenerator.generateId;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WireMockSpringBootITest
@Import({PendingDisputesTestService.class})
public class DebugManualParsingHandlerTest {

    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private DisputeApiTestService disputeApiTestService;
    @Autowired
    private CreatedDisputesTestService createdDisputesTestService;
    @Autowired
    private PendingDisputesTestService pendingDisputesTestService;
    @Autowired
    private DebugManualParsingController debugManualParsingController;

    @Test
    public void testCancelCreateAdjustment() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        debugManualParsingController.cancelPending(getCancelRequest(disputeId));
        assertEquals(DisputeStatus.cancelled, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
    }

    @Test
    public void testCancelPending() {
        var disputeId = createdDisputesTestService.callCreateDisputeRemotely();
        debugManualParsingController.cancelPending(getCancelRequest(disputeId));
        assertEquals(DisputeStatus.cancelled, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
    }

    @Test
    public void testCancelFailed() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
        debugManualParsingController.cancelPending(getCancelRequest(disputeId));
        assertEquals(DisputeStatus.failed, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
    }

    @Test
    public void testApproveCreateAdjustmentWithCallHg() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        debugManualParsingController.approvePending(getApproveRequest(disputeId, false));
        assertEquals(DisputeStatus.create_adjustment, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }

    @Test
    public void testApproveCreateAdjustmentWithSkipHg() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        debugManualParsingController.approvePending(getApproveRequest(disputeId, true));
        assertEquals(DisputeStatus.succeeded, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }

    @Test
    public void testApprovePendingWithSkipHg() {
        var disputeId = createdDisputesTestService.callCreateDisputeRemotely();
        debugManualParsingController.approvePending(getApproveRequest(disputeId, true));
        assertEquals(DisputeStatus.succeeded, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }

    @Test
    public void testApproveFailed() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
        debugManualParsingController.approvePending(getApproveRequest(disputeId, true));
        assertEquals(DisputeStatus.failed, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
    }

    @Test
    public void testBindCreatedCreateAdjustment() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        var providerDisputeId = generateId();
        debugManualParsingController.bindCreated(getBindCreatedRequest(disputeId, providerDisputeId));
        assertEquals(DisputeStatus.create_adjustment, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }

    @Test
    public void testBindCreatedPending() {
        var disputeId = createdDisputesTestService.callCreateDisputeRemotely();
        var providerDisputeId = generateId();
        debugManualParsingController.bindCreated(getBindCreatedRequest(disputeId, providerDisputeId));
        assertEquals(DisputeStatus.pending, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }

    @Test
    public void testBindCreatedManualCreated() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var providerDisputeId = generateId();
        var disputeId = disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId();
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.manual_created);
        debugManualParsingController.bindCreated(getBindCreatedRequest(disputeId, providerDisputeId));
        assertEquals(DisputeStatus.manual_pending, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }

    @Test
    public void testBindCreatedAlreadyExist() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var providerDisputeId = generateId();
        var disputeId = disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId();
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.already_exist_created);
        debugManualParsingController.bindCreated(getBindCreatedRequest(disputeId, providerDisputeId));
        assertEquals(DisputeStatus.pending, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }

    @Test
    @SneakyThrows
    public void testGetDispute() {
        WiremockUtils.mockS3AttachmentDownload();
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        var disputes = debugManualParsingController.getDisputes(getGetDisputeRequest(disputeId, true));
        assertEquals(1, disputes.getDisputes().size());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }
}
