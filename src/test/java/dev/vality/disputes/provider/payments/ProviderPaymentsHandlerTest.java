package dev.vality.disputes.provider.payments;

import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsAdjustmentExtractor;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsService;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.service.external.PartyManagementService;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.disputes.util.TestUrlPaths;
import dev.vality.provider.payments.*;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static dev.vality.disputes.config.NetworkConfig.CALLBACK;
import static dev.vality.disputes.config.NetworkConfig.PROVIDER_PAYMENTS_ADMIN_MANAGEMENT;
import static dev.vality.disputes.util.MockUtil.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@WireMockSpringBootITest
@TestPropertySource(properties = {
        "server.port=${local.server.port}",
        "provider.payments.isProviderCallbackEnabled=true",
})
@SuppressWarnings({"LineLength"})
public class ProviderPaymentsHandlerTest {

    @MockBean
    private InvoicingSrv.Iface invoicingClient;
    @MockBean
    private TokenAuthenticatorSrv.Iface tokenKeeperClient;
    @MockBean
    private ArbiterSrv.Iface bouncerClient;
    @MockBean
    private DominantAsyncService dominantAsyncService;
    @MockBean
    private PartyManagementService partyManagementService;
    @MockBean
    private DominantService dominantService;
    @MockBean
    private ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;
    @Autowired
    private ProviderPaymentsService providerPaymentsService;
    @SpyBean
    private ProviderCallbackDao providerCallbackDao;
    @Autowired
    private ProviderPaymentsAdjustmentExtractor providerPaymentsAdjustmentExtractor;
    @LocalServerPort
    private int serverPort;

    @Test
    @SneakyThrows
    public void testFullFlowCreateAdjustmentWhenFailedPaymentSuccess() {
        when(dominantService.getTerminal(any())).thenReturn(createTerminal().get());
        when(dominantService.getCurrency(any())).thenReturn(createCurrency().get());
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy(String.format("http://127.0.0.1:%s%s", 8023, TestUrlPaths.ADAPTER)).get());
        var providerMock = mock(ProviderPaymentsServiceSrv.Client.class);
        when(providerMock.checkPaymentStatus(any(), any())).thenReturn(createPaymentStatusResult(Long.MAX_VALUE));
        when(providerPaymentsThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        var approveParams = new ArrayList<ApproveParams>();
        var minNumberOfInvocations = 4;
        for (int i = 0; i < minNumberOfInvocations; i++) {
            var invoiceId = String.valueOf(i);
            var invoice = createInvoice(invoiceId, invoiceId);
            when(invoicingClient.getPayment(any(), any())).thenReturn(invoice.getPayments().get(0));
            var request = new ProviderPaymentsCallbackParams()
                    .setInvoiceId(invoiceId)
                    .setPaymentId(invoiceId);
            // 1. callback
            createProviderPaymentsCallbackIface().createAdjustmentWhenFailedPaymentSuccess(request);
            approveParams.add(new ApproveParams(invoiceId, invoiceId));
        }
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(providerCallbackDao, atLeast(minNumberOfInvocations)).save(any()));
        // pendings = 4, approved = 3
        approveParams.removeFirst();
        var request = new ApproveParamsRequest()
                .setApproveAll(false)
                .setApproveParams(approveParams)
                .setApproveReason("test asdj556");
        // 2. approve
        createProviderPaymentsAdminManagementIface().approve(request);
        var providerCallbackIds = new ArrayList<UUID>();
        for (var providerCallback : providerPaymentsService.getPaymentsForHgCall(Integer.MAX_VALUE)) {
            providerCallbackIds.add(providerCallback.getId());
            var reason = providerPaymentsAdjustmentExtractor.getReason(providerCallback);
            var invoicePayment = createInvoicePayment(providerCallback.getPaymentId());
            invoicePayment.setAdjustments(List.of(getCashFlowInvoicePaymentAdjustment("adjustmentId", reason)));
            when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
            when(invoicingClient.createPaymentAdjustment(any(), any(), any()))
                    .thenReturn(getCapturedInvoicePaymentAdjustment("adjustmentId", reason));
            // 3. hg.createPaymentAdjustment
            providerPaymentsService.callHgForCreateAdjustment(providerCallback);
        }
        for (var providerCallbackId : providerCallbackIds) {
            var providerCallback = providerCallbackDao.getProviderCallbackForUpdateSkipLocked(providerCallbackId);
            assertEquals(ProviderPaymentsStatus.succeeded, providerCallback.getStatus());
        }
        assertEquals(minNumberOfInvocations - providerCallbackIds.size(),
                providerCallbackDao.getAllPendingProviderCallbacksForUpdateSkipLocked().size());
    }

    private ProviderPaymentsCallbackServiceSrv.Iface createProviderPaymentsCallbackIface() throws URISyntaxException {
        return new THSpawnClientBuilder()
                .withAddress(new URI("http://127.0.0.1:" + serverPort + CALLBACK))
                .withNetworkTimeout(5000)
                .build(ProviderPaymentsCallbackServiceSrv.Iface.class);
    }

    private ProviderPaymentsAdminManagementServiceSrv.Iface createProviderPaymentsAdminManagementIface() throws URISyntaxException {
        return new THSpawnClientBuilder()
                .withAddress(new URI("http://127.0.0.1:" + serverPort + PROVIDER_PAYMENTS_ADMIN_MANAGEMENT))
                .withNetworkTimeout(5000)
                .build(ProviderPaymentsAdminManagementServiceSrv.Iface.class);
    }

    private static PaymentStatusResult createPaymentStatusResult(long changedAmount) {
        return new PaymentStatusResult(true).setChangedAmount(changedAmount);
    }
}
