package dev.vality.disputes.admin.management;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.vality.disputes.admin.AdminCallbackServiceSrv;
import dev.vality.disputes.admin.AdminManagementServiceSrv;
import dev.vality.disputes.admin.AttachmentRef;
import dev.vality.disputes.admin.Dispute;
import dev.vality.disputes.admin.DisputeParams;
import dev.vality.disputes.admin.DisputeParamsRequest;
import dev.vality.disputes.admin.callback.CallbackNotifier;
import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.dao.FileMetaDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.FileMeta;
import dev.vality.provider.payments.PaymentStatusResult;
import dev.vality.provider.payments.ProviderPaymentsServiceSrv;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.SneakyThrows;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static dev.vality.disputes.config.NetworkConfig.ADMIN_MANAGEMENT;
import static dev.vality.disputes.util.MockUtil.*;
import static dev.vality.disputes.util.OpenApiUtil.*;
import static dev.vality.disputes.util.TestUrlPaths.MOCK_DOWNLOAD;
import static dev.vality.disputes.util.TestUrlPaths.S3_PATH;
import static dev.vality.disputes.util.ValuesGenerator.generateId;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
@TestPropertySource(properties = "service.disputes-tg-bot.admin.enabled=true")
public class DebugAdminManagementHandlerTest extends AbstractMockitoConfig {

    @Autowired
    private DebugAdminManagementController debugAdminManagementController;
    @Autowired
    private FileMetaDao fileMetaDao;
    @Autowired
    private CallbackNotifier callbackNotifier;
    @MockitoBean
    private AdminCallbackServiceSrv.Iface adminCallbackDisputesTgBotClient;

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
    public void testApproveCreateAdjustmentWithSkipHgAndChangedAmount() {
        var disputeId = pendingFlowHandler.handlePending();
        var dispute = disputeDao.get(disputeId);
        debugAdminManagementController.approvePending(
                getApproveRequest(dispute.getInvoiceId(), dispute.getPaymentId(), true, 101L));
        var updatedDispute = disputeDao.get(disputeId);
        assertEquals(DisputeStatus.succeeded, updatedDispute.getStatus());
        assertEquals(101L, updatedDispute.getChangedAmount());
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
    public void testAttachmentRefsInCallbackAndGetDisputes() {
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute("20McecNnWoy", "1").getDisputeId());
        var dispute = disputeDao.get(disputeId);
        fileMetaDao.save(new FileMeta(generateId(), disputeId, "application/pdf"));
        var files = fileMetaDao.getDisputeFiles(disputeId);
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn(wiremockAddressesHolder.getDownloadUrl());
        var expectedRefs = files.stream()
                .map(file -> new AttachmentRef(wiremockAddressesHolder.getDownloadUrl(), file.getMimeType()))
                .toList();
        var client = new THSpawnClientBuilder()
                .withAddress(new URI("http://127.0.0.1:" + serverPort + ADMIN_MANAGEMENT))
                .withNetworkTimeout(5000)
                .build(AdminManagementServiceSrv.Iface.class);
        var request = new DisputeParamsRequest()
                .setDisputeParams(List.of(new DisputeParams(dispute.getInvoiceId(), dispute.getPaymentId())))
                .setWithAttachments(false);

        var metadataOnly = client.getDisputes(request).getDisputes().getFirst();
        assertEquals(expectedRefs, metadataOnly.getAttachmentRefs().orElseThrow());
        assertFalse(metadataOnly.isSetAttachments());

        callbackNotifier.notify(dispute);
        var callback = ArgumentCaptor.forClass(Dispute.class);
        org.mockito.Mockito.verify(adminCallbackDisputesTgBotClient, timeout(5000)).notify(callback.capture());
        var notification = new Dispute();
        new TDeserializer().deserialize(notification, new TSerializer().serialize(callback.getValue()));
        assertEquals(disputeId.toString(), notification.getDisputeId());
        assertEquals(expectedRefs, notification.getAttachmentRefs().orElseThrow());
        assertFalse(notification.isSetAttachments());
        org.mockito.Mockito.verify(fileStorageClient, times(files.size() * 2)).generateDownloadUrl(any(), any());
        verify(0, getRequestedFor(urlEqualTo(S3_PATH + MOCK_DOWNLOAD)));

        var data = new byte[]{1, 2, 3};
        stubFor(get(urlEqualTo(S3_PATH + MOCK_DOWNLOAD)).willReturn(ok().withBody(data)));
        var downloadUri = URI.create(wiremockAddressesHolder.getDownloadUrl());
        var wireMock = new WireMock(downloadUri.getHost(), downloadUri.getPort());
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenAnswer(invocation -> {
            wireMock.verifyThat(0, getRequestedFor(urlEqualTo(S3_PATH + MOCK_DOWNLOAD)));
            return wiremockAddressesHolder.getDownloadUrl();
        });
        var withAttachments = client.getDisputes(request.setWithAttachments(true)).getDisputes().getFirst();
        assertEquals(expectedRefs, withAttachments.getAttachmentRefs().orElseThrow());
        var attachments = withAttachments.getAttachments().orElseThrow();
        assertEquals(files.size(), attachments.size());
        for (int i = 0; i < files.size(); i++) {
            assertEquals(files.get(i).getMimeType(), attachments.get(i).getMimeType());
            assertArrayEquals(data, attachments.get(i).getData());
        }
        verify(files.size(), getRequestedFor(urlEqualTo(S3_PATH + MOCK_DOWNLOAD)));
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
