package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.ProviderDisputesServiceSrv;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.schedule.service.config.DisputeApiTestService;
import dev.vality.disputes.schedule.service.config.DisputeApiTestServiceConfig;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.testutil.MockUtil;
import dev.vality.file.storage.FileStorageSrv;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import static dev.vality.disputes.testutil.MockUtil.*;
import static dev.vality.testcontainers.annotations.util.ValuesGenerator.generateId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
@Import({DisputeApiTestServiceConfig.class, DisputeApiTestService.class})
public class CreatedDisputesServiceTest {

    @MockBean
    private ProviderIfaceBuilder providerIfaceBuilder;
    @Autowired
    private InvoicingSrv.Iface invoicingClient;
    @MockBean
    private DominantService dominantService;
    @Autowired
    private FileStorageSrv.Iface fileStorageClient;
    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private CreatedDisputesService createdDisputesService;
    @Autowired
    private DisputeApiTestService disputeApiTestService;

    @Test
    @SneakyThrows
    public void testPaymentNotFound() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId();
        var dispute = disputeDao.get(Long.parseLong(disputeId));
        createdDisputesService.callCreateDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.failed, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        assertEquals(ErrorReason.PAYMENT_NOT_FOUND, disputeDao.get(Long.parseLong(disputeId)).get().getErrorMessage());
    }

    @Test
    @SneakyThrows
    public void testSkipDisputeWhenPaymentNonFinalStatus() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId();
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        var dispute = disputeDao.get(Long.parseLong(disputeId));
        createdDisputesService.callCreateDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.created, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }

    @Test
    @SneakyThrows
    public void testNoAttachments() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId();
        var invoicePayment = MockUtil.createInvoicePayment(paymentId);
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        var dispute = disputeDao.get(Long.parseLong(disputeId));
        createdDisputesService.callCreateDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.failed, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        assertEquals(ErrorReason.NO_ATTACHMENTS, disputeDao.get(Long.parseLong(disputeId)).get().getErrorMessage());
    }

    @Test
    @SneakyThrows
    public void testManualCreatedWhenIsNotProvidersDisputesApiExist() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId();
        var invoicePayment = MockUtil.createInvoicePayment(paymentId);
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn("http://localhost:8022/");
        when(dominantService.getTerminal(any())).thenReturn(createTerminal().get());
        var dispute = disputeDao.get(Long.parseLong(disputeId));
        createdDisputesService.callCreateDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.manual_created, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }

    @Test
    @SneakyThrows
    public void testDisputeCreatedSuccessResult() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var providerDisputeId = generateId();
        var disputeId = disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId();
        var invoicePayment = MockUtil.createInvoicePayment(paymentId);
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn("http://localhost:8022/");
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.createDispute(any())).thenReturn(createDisputeCreatedSuccessResult(providerDisputeId));
        when(providerIfaceBuilder.buildTHSpawnClient(any(), any())).thenReturn(providerMock);
        var dispute = disputeDao.get(Long.parseLong(disputeId));
        createdDisputesService.callCreateDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.pending, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }

    @Test
    @SneakyThrows
    public void testDisputeCreatedFailResult() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var providerDisputeId = generateId();
        var disputeId = disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId();
        var invoicePayment = MockUtil.createInvoicePayment(paymentId);
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn("http://localhost:8022/");
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.createDispute(any())).thenReturn(createDisputeCreatedFailResult(providerDisputeId));
        when(providerIfaceBuilder.buildTHSpawnClient(any(), any())).thenReturn(providerMock);
        var dispute = disputeDao.get(Long.parseLong(disputeId));
        createdDisputesService.callCreateDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.failed, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
    }

    @Test
    @SneakyThrows
    public void testDisputeCreatedAlreadyExistResult() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var providerDisputeId = generateId();
        var disputeId = disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId();
        var invoicePayment = MockUtil.createInvoicePayment(paymentId);
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn("http://localhost:8022/");
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.createDispute(any())).thenReturn(createDisputeAlreadyExistResult(providerDisputeId));
        when(providerIfaceBuilder.buildTHSpawnClient(any(), any())).thenReturn(providerMock);
        var dispute = disputeDao.get(Long.parseLong(disputeId));
        createdDisputesService.callCreateDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.already_exist_created, disputeDao.get(Long.parseLong(disputeId)).get().getStatus());
        disputeDao.update(Long.parseLong(disputeId), DisputeStatus.failed);
    }
}
