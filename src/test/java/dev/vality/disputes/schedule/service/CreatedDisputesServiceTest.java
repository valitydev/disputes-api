package dev.vality.disputes.schedule.service;

import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import dev.vality.disputes.schedule.core.CreatedDisputesService;
import dev.vality.disputes.schedule.service.config.CreatedDisputesTestService;
import dev.vality.disputes.schedule.service.config.DisputeApiTestService;
import dev.vality.disputes.schedule.service.config.WiremockAddressesHolder;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.util.MockUtil;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.provider.payments.PaymentStatusResult;
import dev.vality.provider.payments.ProviderPaymentsServiceSrv;
import lombok.SneakyThrows;
import org.apache.thrift.TException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static dev.vality.disputes.constant.ModerationPrefix.DISPUTES_UNKNOWN_MAPPING;
import static dev.vality.disputes.util.MockUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
@Import({CreatedDisputesTestService.class})
@SuppressWarnings({"LineLength", "VariableDeclarationUsageDistance"})
public class CreatedDisputesServiceTest {

    @Autowired
    private ProviderDisputesThriftInterfaceBuilder providerDisputesThriftInterfaceBuilder;
    @Autowired
    private ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;
    @Autowired
    private DominantService dominantService;
    @Autowired
    private InvoicingSrv.Iface invoicingClient;
    @Autowired
    private FileStorageSrv.Iface fileStorageClient;
    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private CreatedDisputesService createdDisputesService;
    @Autowired
    private DisputeApiTestService disputeApiTestService;
    @Autowired
    private WiremockAddressesHolder wiremockAddressesHolder;
    @Autowired
    private CreatedDisputesTestService createdDisputesTestService;
    @LocalServerPort
    private int serverPort;

    @Test
    @SneakyThrows
    public void testPaymentNotFound() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
        assertEquals(ErrorMessage.PAYMENT_NOT_FOUND, disputeDao.get(disputeId).getErrorMessage());
    }

    @Test
    @SneakyThrows
    public void testNoAttachments() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        when(dominantService.getTerminal(any())).thenReturn(createTerminal().get());
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        mockFailStatusProviderPayment();
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
        assertEquals(ErrorMessage.NO_ATTACHMENTS, disputeDao.get(disputeId).getErrorMessage());
    }

    @Test
    @SneakyThrows
    public void testManualPendingWhenIsNotProviderDisputesApiExist() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn(wiremockAddressesHolder.getDownloadUrl());
        when(dominantService.getTerminal(any())).thenReturn(createTerminal().get());
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        mockFailStatusProviderPayment();
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.manual_pending, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    public void testDisputeCreatedSuccessResult() {
        var disputeId = createdDisputesTestService.callCreateDisputeRemotely();
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testDisputeCreatedFailResult() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn(wiremockAddressesHolder.getDownloadUrl());
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.createDispute(any())).thenReturn(createDisputeCreatedFailResult());
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        mockFailStatusProviderPayment();
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
    }

    @Test
    @SneakyThrows
    public void testManualPendingWhenDisputeCreatedFailResultWithDisputesUnknownMapping() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn(wiremockAddressesHolder.getDownloadUrl());
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        var disputeCreatedFailResult = createDisputeCreatedFailResult();
        disputeCreatedFailResult.getFailResult().getFailure().setCode(DISPUTES_UNKNOWN_MAPPING);
        when(providerMock.createDispute(any())).thenReturn(disputeCreatedFailResult);
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        mockFailStatusProviderPayment();
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.manual_pending, disputeDao.get(disputeId).getStatus());
        assertTrue(disputeDao.get(disputeId).getErrorMessage().contains(DISPUTES_UNKNOWN_MAPPING));
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testManualPendingWhenUnexpectedResultMapping() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn(wiremockAddressesHolder.getDownloadUrl());
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        // routeUrl = "http://127.0.0.1:8023/disputes" == exist api
        when(dominantService.getProxy(any())).thenReturn(createProxyWithRealAddress(serverPort).get());
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.createDispute(any())).thenThrow(getUnexpectedResultWException());
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        mockFailStatusProviderPayment();
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.manual_pending, disputeDao.get(disputeId).getStatus());
        assertTrue(disputeDao.get(disputeId).getErrorMessage().contains("Unexpected result"));
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testManualPendingWhenUnexpectedResult() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn(wiremockAddressesHolder.getDownloadUrl());
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxyNotFoundCase(serverPort).get());
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.createDispute(any())).thenThrow(getUnexpectedResultWException());
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        mockFailStatusProviderPayment();
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.manual_pending, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testDisputeCreatedAlreadyExistResult() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn(wiremockAddressesHolder.getDownloadUrl());
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.createDispute(any())).thenReturn(createDisputeAlreadyExistResult());
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        mockFailStatusProviderPayment();
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.already_exist_created, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    private void mockFailStatusProviderPayment() throws TException {
        var providerPaymentMock = mock(ProviderPaymentsServiceSrv.Client.class);
        when(providerPaymentMock.checkPaymentStatus(any(), any())).thenReturn(new PaymentStatusResult(false));
        when(providerPaymentsThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerPaymentMock);
    }
}
