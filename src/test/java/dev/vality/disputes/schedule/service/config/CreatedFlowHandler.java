package dev.vality.disputes.schedule.service.config;

import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.config.WiremockAddressesHolder;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import dev.vality.disputes.schedule.core.CreatedDisputesService;
import dev.vality.disputes.schedule.service.ProviderDisputesThriftInterfaceBuilder;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.util.MockUtil;
import dev.vality.disputes.util.TestUrlPaths;
import dev.vality.file.storage.FileStorageSrv;
import dev.vality.provider.payments.PaymentStatusResult;
import dev.vality.provider.payments.ProviderPaymentsServiceSrv;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.*;
import static dev.vality.testcontainers.annotations.util.ValuesGenerator.generateId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RequiredArgsConstructor
public class CreatedFlowHandler {

    private final InvoicingSrv.Iface invoicingClient;
    private final FileStorageSrv.Iface fileStorageClient;
    private final DisputeDao disputeDao;
    private final DominantService dominantService;
    private final CreatedDisputesService createdDisputesService;
    private final ProviderDisputesThriftInterfaceBuilder providerDisputesThriftInterfaceBuilder;
    private final ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;
    private final WiremockAddressesHolder wiremockAddressesHolder;
    private final MerchantApiMvcPerformer merchantApiMvcPerformer;

    @SneakyThrows
    public UUID handleCreate() {
        var paymentId = "1";
        when(invoicingClient.getPayment(any(), any())).thenReturn(MockUtil.createInvoicePayment(paymentId));
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn(wiremockAddressesHolder.getDownloadUrl());
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(
                createProxy(String.format("http://127.0.0.1:%s%s", 8023, TestUrlPaths.ADAPTER)).get());
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        var providerDisputeId = generateId();
        when(providerMock.createDispute(any())).thenReturn(createDisputeCreatedSuccessResult(providerDisputeId));
        when(providerDisputesThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        mockFailStatusProviderPayment();
        var invoiceId = "20McecNnWoy";
        var disputeId = UUID.fromString(merchantApiMvcPerformer.createDispute(invoiceId, paymentId).getDisputeId());
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute);
        assertEquals(DisputeStatus.pending, disputeDao.get(disputeId).getStatus());
        return disputeId;
    }

    @SneakyThrows
    public void mockFailStatusProviderPayment() {
        var providerPaymentMock = mock(ProviderPaymentsServiceSrv.Client.class);
        when(providerPaymentMock.checkPaymentStatus(any(), any())).thenReturn(new PaymentStatusResult(false));
        when(providerPaymentsThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerPaymentMock);
    }

    @SneakyThrows
    public void mockSuccessStatusProviderPayment() {
        var providerPaymentMock = mock(ProviderPaymentsServiceSrv.Client.class);
        when(providerPaymentMock.checkPaymentStatus(any(), any())).thenReturn(
                new PaymentStatusResult(true).setChangedAmount(Long.MAX_VALUE));
        when(providerPaymentsThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerPaymentMock);
    }
}
