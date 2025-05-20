package dev.vality.disputes.provider.payments;

import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.util.TestUrlPaths;
import dev.vality.provider.payments.ProviderPaymentsCallbackParams;
import dev.vality.provider.payments.ProviderPaymentsCallbackServiceSrv;
import dev.vality.provider.payments.ProviderPaymentsServiceSrv;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.SneakyThrows;
import org.apache.thrift.TException;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static dev.vality.disputes.config.NetworkConfig.CALLBACK;
import static dev.vality.disputes.util.MockUtil.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@WireMockSpringBootITest
@TestPropertySource(properties = {
        "server.port=${local.server.port}",
        "provider.payments.isProviderCallbackEnabled=true",
})

public class ProviderCallbackHandlerTest extends AbstractMockitoConfig {

    @Test
    @SneakyThrows
    public void testSuccess() {
        when(dominantService.getTerminal(any())).thenReturn(createTerminal().get());
        when(dominantService.getCurrency(any())).thenReturn(createCurrency().get());
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getProxy(any())).thenReturn(
                createProxy(String.format("http://127.0.0.1:%s%s", 8023, TestUrlPaths.ADAPTER)).get());
        var providerMock = mock(ProviderPaymentsServiceSrv.Client.class);
        when(providerMock.checkPaymentStatus(any(), any())).thenReturn(createPaymentStatusResult());
        when(providerPaymentsThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(providerMock);
        var minNumberOfInvocations = 4;
        for (int i = 0; i < minNumberOfInvocations; i++) {
            var invoiceId = String.valueOf(i);
            var paymentId = String.valueOf(i);
            executeCallbackIFace(invoiceId, paymentId);
        }
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(providerCallbackDao, atLeast(minNumberOfInvocations)).save(any()));
        var providerCallbackIds = new ArrayList<UUID>();
        for (var providerCallback : providerPaymentsService.getPaymentsForHgCall(Integer.MAX_VALUE)) {
            providerCallbackIds.add(providerCallback.getId());
            var reason = providerPaymentsAdjustmentExtractor.getReason(providerCallback);
            var invoicePayment = createInvoicePayment(providerCallback.getPaymentId());
            invoicePayment.setAdjustments(List.of(getCashFlowInvoicePaymentAdjustment("adjustmentId", reason)));
            when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
            when(invoicingClient.createPaymentAdjustment(any(), any(), any()))
                    .thenReturn(getCapturedInvoicePaymentAdjustment("adjustmentId", reason));
            providerPaymentsService.callHgForCreateAdjustment(providerCallback);
        }
        for (var providerCallbackId : providerCallbackIds) {
            var providerCallback = providerCallbackDao.getProviderCallbackForUpdateSkipLocked(providerCallbackId);
            assertEquals(ProviderPaymentsStatus.succeeded, providerCallback.getStatus());
        }
    }

    private void executeCallbackIFace(String invoiceId, String paymentId) throws TException, URISyntaxException {
        var invoice = createInvoice(invoiceId, paymentId);
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoice.getPayments().getFirst());
        var request = new ProviderPaymentsCallbackParams()
                .setInvoiceId(invoiceId)
                .setPaymentId(paymentId);
        createProviderPaymentsCallbackIface().createAdjustmentWhenFailedPaymentSuccess(request);
    }

    private ProviderPaymentsCallbackServiceSrv.Iface createProviderPaymentsCallbackIface() throws URISyntaxException {
        return new THSpawnClientBuilder()
                .withAddress(new URI("http://127.0.0.1:" + serverPort + CALLBACK))
                .withNetworkTimeout(5000)
                .build(ProviderPaymentsCallbackServiceSrv.Iface.class);
    }
}
