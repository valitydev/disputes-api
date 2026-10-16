package dev.vality.disputes.retry;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.config.properties.RemoteCallProperties;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.converter.TransactionInfoThriftConverter;
import dev.vality.disputes.provider.payments.schedule.ProviderPaymentsTask;
import dev.vality.disputes.remote.RemoteCallBudget;
import dev.vality.disputes.remote.BudgetedClientFactory;
import dev.vality.provider.payments.ProviderPaymentsServiceSrv;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.thrift.TException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.SocketTimeoutException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static dev.vality.disputes.util.MockUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
@TestPropertySource(properties = {
        "background-processing.remote-call.connect-timeout=100ms",
        "background-processing.remote-call.pool-timeout=100ms",
        "background-processing.remote-call.max-duration=100ms",
        "background-processing.retry.initial-delay=1s",
        "background-processing.retry.max-delay=2s",
        "http-client.max-total-pooling=1",
        "http-client.default-max-per-route=1"
})
class BackgroundQueueRecoveryTest extends AbstractMockitoConfig {

    @Autowired
    private BackgroundProcessing backgroundProcessing;
    @Autowired
    private ScheduledRetryService scheduledRetryService;
    @Autowired
    private TransactionInfoThriftConverter transactionInfoThriftConverter;
    @Autowired
    @Qualifier("gatewayCheckHttpClient")
    private CloseableHttpClient budgetedHttpClient;
    @Autowired
    private RemoteCallBudget remoteCallBudget;
    @Autowired
    private BudgetedClientFactory budgetedClientFactory;
    @Autowired
    private RemoteCallProperties remoteCallProperties;
    @Autowired
    private CloseableHttpClient httpClient;
    @Value("${wiremock.server.baseUrl}")
    private String baseUrl;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutMovesOldCallbackBehindHealthyWork(boolean exhaustPool) throws Exception {
        stubFor(post(urlEqualTo("/slow-rpc")).willReturn(aResponse().withFixedDelay(3000)));
        stubFor(get(urlEqualTo("/hold-connection")).willReturn(ok("unconsumed body")));
        stubFor(get(urlEqualTo("/outside-background")).willReturn(ok("ok").withFixedDelay(300)));
        var remote = new THSpawnClientBuilder().withHttpClient(budgetedHttpClient).withNetworkTimeout(10_000)
                .withAddress(URI.create(baseUrl + "/slow-rpc")).build(InvoicingSrv.Iface.class);
        var blocked = callback("blocked", 24);
        var healthy = callback("healthy", 12);
        when(invoicingClient.getPayment(blocked.getInvoiceId(), "1"))
                .thenAnswer(invocation -> remote.getPayment(blocked.getInvoiceId(), "1"));
        var captured = createInvoicePayment("1");
        captured.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(healthy.getInvoiceId(), "1")).thenReturn(captured);

        try (var workers = Executors.newSingleThreadExecutor()) {
            var task = new ProviderPaymentsTask(workers, providerPaymentsService, backgroundProcessing);
            ReflectionTestUtils.setField(task, "batchSize", 1);
            var started = System.nanoTime();
            try (var held = exhaustPool
                    ? budgetedHttpClient.executeOpen(null, new HttpGet(baseUrl + "/hold-connection"), null) : null) {
                task.processPending();
            }
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
            var deferred = providerCallbackDao.get(blocked.getInvoiceId(), "1");
            assertThat(deferred.getStatus()).isEqualTo(ProviderPaymentsStatus.create_adjustment);
            assertThat(deferred.getCreatedAt()).isEqualTo(blocked.getCreatedAt());
            assertThat(deferred.getAmount()).isEqualTo(blocked.getAmount());
            assertThat(deferred.getPaymentStatusSuccess()).isTrue();
            assertThat(deferred.getNextCheckAfter()).isAfter(now()).isBefore(now().plusSeconds(3));
            assertThat(providerPaymentsService.getPaymentsForHgCall(1)).extracting(ProviderCallback::getId)
                    .containsExactly(healthy.getId());
            task.processPending();
            assertThat(providerCallbackDao.get(healthy.getInvoiceId(), "1").getStatus())
                    .isEqualTo(ProviderPaymentsStatus.succeeded);
            assertThat(providerPaymentsService.getPaymentsForHgCall(1)).isEmpty();
            assertThat(workers.submit(() -> {
                assertThat(remoteCallBudget.isBackground()).isFalse();
                return httpClient.execute(new HttpGet(baseUrl + "/outside-background"),
                        new BasicHttpClientResponseHandler());
            }).get(2, TimeUnit.SECONDS)).isEqualTo("ok");
        }
        assertThat(remoteCallBudget.isBackground()).isFalse();
    }

    @Test
    void adapterTimeoutRollsBackEnrichmentButCommitsDisputeRetry() throws Exception {
        var id = UUID.fromString(merchantApiMvcPerformer.createDispute("created-timeout", "1").getDisputeId());
        final var selected = disputeDao.get(id);
        disputeDao.updatePaymentRiskData(id, "original-email", "original-risk");
        when(invoicingClient.getPayment(any(), any())).thenReturn(createInvoicePayment("1"));
        when(invoicingClient.getEvents(any(), any())).thenReturn(List.of());
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getTerminal(any())).thenReturn(createTerminal().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        stubFor(post(urlEqualTo("/slow-adapter")).willReturn(aResponse().withFixedDelay(3000)));
        var adapter = budgetedClientFactory.spawnClientBuilder().withNetworkTimeout(10_000)
                .withAddress(URI.create(baseUrl + "/slow-adapter")).build(ProviderPaymentsServiceSrv.Iface.class);
        when(providerPaymentsThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(adapter);

        backgroundProcessing.processDispute(selected,
                () -> createdDisputesService.callCreateDisputeRemotely(selected));

        var current = disputeDao.get(id);
        assertThat(current.getStatus()).isEqualTo(DisputeStatus.created);
        assertThat(current.getPayerEmail()).isEqualTo("original-email");
        assertThat(current.getRiskScore()).isEqualTo("original-risk");
        assertThat(current.getNextCheckAfter()).isAfter(now());
        assertThat(createdDisputesService.getCreatedSkipLocked(10)).isEmpty();
        var later = now().plusMinutes(10).withNano(0);
        disputeDao.setNextStepToPending(id, later, current.getPollingBefore());
        scheduledRetryService.deferDispute(id, DisputeStatus.created);
        assertThat(disputeDao.get(id).getStatus()).isEqualTo(DisputeStatus.pending);
        assertThat(disputeDao.get(id).getNextCheckAfter()).isEqualTo(later);
    }

    @Test
    void lostAdjustmentResponseIsReconciledBeforeRetryingTheWrite() throws Exception {
        var selected = callback("lost-response", 24);
        var transactionInfo = new TransactionInfo("linked-order", Map.of("rrn", "123"));
        selected.setTransactionInfo(transactionInfoThriftConverter.serialize(transactionInfo));
        providerCallbackDao.update(selected);
        var invoicePayment = createInvoicePayment("1");
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        var reason = providerPaymentsAdjustmentExtractor.getReason(selected);
        doAnswer(invocation -> {
            var params = (InvoicePaymentAdjustmentParams) invocation.getArgument(2);
            if (params.getScenario().isSetTransactionInfo()) {
                invoicePayment.setAdjustments(List.of(
                        getTransactionInfoInvoicePaymentAdjustment("accepted-remotely", reason, transactionInfo)));
                throw new TException(new SocketTimeoutException("response was lost after acceptance"));
            }
            return null;
        }).when(invoicingClient).createPaymentAdjustment(any(), any(), any());

        backgroundProcessing.processProviderCallback(selected,
                () -> providerPaymentsService.callHgForCreateAdjustment(selected));
        var deferred = providerCallbackDao.get(selected.getInvoiceId(), "1");
        assertThat(deferred.getStatus()).isEqualTo(ProviderPaymentsStatus.create_adjustment);
        assertThat(deferred.getNextCheckAfter()).isAfter(now());
        assertThat(deferred.getTransactionInfo()).containsExactly(selected.getTransactionInfo());
        backgroundProcessing.processProviderCallback(selected,
                () -> providerPaymentsService.callHgForCreateAdjustment(selected));
        verify(invoicingClient, times(1)).createPaymentAdjustment(any(), any(), any());

        providerCallbackDao.updateNextCheckAfter(selected.getId(), now().minusSeconds(1));
        backgroundProcessing.processProviderCallback(selected,
                () -> providerPaymentsService.callHgForCreateAdjustment(selected));
        verify(invoicingClient, times(2)).createPaymentAdjustment(any(), any(), any());
        var finished = providerCallbackDao.get(selected.getInvoiceId(), "1");
        assertThat(finished.getStatus()).isEqualTo(ProviderPaymentsStatus.succeeded);
        assertThat(finished.getId()).isEqualTo(selected.getId());
        scheduledRetryService.deferProviderCallback(selected.getId());
        assertThat(providerCallbackDao.get(selected.getInvoiceId(), "1").getNextCheckAfter())
                .isEqualTo(finished.getNextCheckAfter());
    }

    @Test
    void budgetBoundsCompositeCallsAndContinuouslyArrivingResponse() {
        stubFor(get(urlEqualTo("/budget-first")).willReturn(ok("first").withFixedDelay(250)));
        stubFor(get(urlEqualTo("/budget-second")).willReturn(ok("second").withFixedDelay(250)));
        stubFor(get(urlEqualTo("/budget-trickle")).willReturn(ok("x".repeat(400))
                .withChunkedDribbleDelay(20, 1600)));
        var previous = remoteCallProperties.getMaxDuration();
        remoteCallProperties.setMaxDuration(Duration.ofMillis(400));
        try {
            var started = System.nanoTime();
            var compositeError = catchThrowable(() -> remoteCallBudget.runInBackground(() ->
                    remoteCallBudget.asOneOperation(() -> {
                        budgetedGet("/budget-first");
                        budgetedGet("/budget-second");
                    })));
            assertThat(TransientErrorClassifier.isTransient(compositeError)).isTrue();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
            WireMock.verify(getRequestedFor(urlEqualTo("/budget-first")));
            WireMock.verify(getRequestedFor(urlEqualTo("/budget-second")));

            started = System.nanoTime();
            var trickleError = catchThrowable(() -> remoteCallBudget.runInBackground(() ->
                    budgetedGet("/budget-trickle")));
            assertThat(TransientErrorClassifier.isTransient(trickleError)).isTrue();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
            assertThat(remoteCallBudget.isBackground()).isFalse();
        } finally {
            remoteCallProperties.setMaxDuration(previous);
        }
    }

    private void budgetedGet(String path) {
        try {
            budgetedHttpClient.execute(new HttpGet(baseUrl + path), new BasicHttpClientResponseHandler());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private ProviderCallback callback(String invoiceId, int ageHours) {
        var callback = new ProviderCallback();
        callback.setInvoiceId(invoiceId);
        callback.setPaymentId("1");
        callback.setAmount(100L);
        callback.setPaymentStatusSuccess(true);
        callback.setCreatedAt(now().minusHours(ageHours));
        callback.setNextCheckAfter(now().minusHours(ageHours));
        providerCallbackDao.save(callback);
        return providerCallbackDao.get(invoiceId, "1");
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
