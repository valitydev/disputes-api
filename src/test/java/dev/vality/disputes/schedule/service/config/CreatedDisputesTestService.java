package dev.vality.disputes.schedule.service.config;

import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.schedule.service.CreatedDisputesService;
import dev.vality.disputes.schedule.service.ProviderIfaceBuilder;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.util.MockUtil;
import dev.vality.disputes.util.TestUrlPaths;
import dev.vality.file.storage.FileStorageSrv;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.*;
import static dev.vality.testcontainers.annotations.util.ValuesGenerator.generateId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestComponent
@Import({DisputeApiTestService.class, RemoteClientTestConfig.class, DefaultRemoteClientTestConfig.class, CallbackNotifierTestConfig.class})
@SuppressWarnings({"ParameterName", "LineLength"})
public class CreatedDisputesTestService {

    @Autowired
    private ProviderIfaceBuilder providerIfaceBuilder;
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

    @SneakyThrows
    public UUID callCreateDisputeRemotely() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var providerDisputeId = generateId();
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        var invoicePayment = MockUtil.createInvoicePayment(paymentId);
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        when(fileStorageClient.generateDownloadUrl(any(), any())).thenReturn(wiremockAddressesHolder.getDownloadUrl());
        var terminal = createTerminal().get();
        terminal.getOptions().putAll(getOptions());
        when(dominantService.getTerminal(any())).thenReturn(terminal);
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy(String.format("http://127.0.0.1:%s%s", 8023, TestUrlPaths.ADAPTER)).get());
        var providerMock = mock(ProviderDisputesServiceSrv.Client.class);
        when(providerMock.createDispute(any())).thenReturn(createDisputeCreatedSuccessResult(providerDisputeId));
        when(providerIfaceBuilder.buildTHSpawnClient(any())).thenReturn(providerMock);
        var dispute = disputeDao.get(disputeId);
        createdDisputesService.callCreateDisputeRemotely(dispute.get());
        assertEquals(DisputeStatus.pending, disputeDao.get(disputeId).get().getStatus());
        return disputeId;
    }
}
