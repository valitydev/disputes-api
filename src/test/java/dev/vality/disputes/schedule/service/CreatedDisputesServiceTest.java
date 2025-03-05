package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentRefunded;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.util.MockUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.UUID;

import static dev.vality.disputes.constant.ModerationPrefix.DISPUTES_UNKNOWN_MAPPING;
import static dev.vality.disputes.util.MockUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
@SuppressWarnings({"VariableDeclarationUsageDistance"})
public class CreatedDisputesServiceTest extends AbstractMockitoConfig {

    @LocalServerPort
    private int serverPort;

    @Test
    public void testDisputeCreatedSuccessResult() {
        var disputeId = createdFlowHandler.handleCreate();
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testPaymentNotFound() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
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
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        when(dominantService.getTerminal(any())).thenReturn(createTerminal().get());
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        createdFlowHandler.mockFailStatusProviderPayment();
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
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn(wiremockAddressesHolder.getDownloadUrl());
        when(dominantService.getTerminal(any())).thenReturn(createTerminal().get());
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        createdFlowHandler.mockFailStatusProviderPayment();
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.manual_pending, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testDisputeCreatedFailResult() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
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
        createdFlowHandler.mockFailStatusProviderPayment();
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
    }

    @Test
    @SneakyThrows
    public void testManualPendingWhenDisputeCreatedFailResultWithDisputesUnknownMapping() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
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
        createdFlowHandler.mockFailStatusProviderPayment();
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
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
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
        createdFlowHandler.mockFailStatusProviderPayment();
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
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
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
        createdFlowHandler.mockFailStatusProviderPayment();
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
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
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
        createdFlowHandler.mockFailStatusProviderPayment();
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.already_exist_created, disputeDao.get(disputeId).getStatus());
        disputeDao.finishFailed(disputeId, null);
    }

    @Test
    @SneakyThrows
    public void testFailedWhenInvoicePaymentStatusIsRefunded() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
        var invoicePayment = createInvoicePayment(paymentId);
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.refunded(new InvoicePaymentRefunded()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
    }

    @Test
    @SneakyThrows
    public void testFailedWhenInvoicePaymentStatusIsCaptured() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
        var invoicePayment = createInvoicePayment(paymentId);
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
    }
}
