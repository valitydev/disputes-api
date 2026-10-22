package dev.vality.disputes.retry;

import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.disputes.config.AbstractMockitoConfig;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.converter.TransactionInfoThriftConverter;
import dev.vality.disputes.provider.payments.schedule.ProviderPaymentsTask;
import dev.vality.provider.payments.ProviderPaymentsServiceSrv;
import dev.vality.woody.api.flow.error.WRuntimeException;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import org.apache.thrift.TException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static dev.vality.disputes.util.MockUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
@TestPropertySource(properties = {
        "background-processing.retry.initial-delay=1s",
        "background-processing.retry.max-delay=2s"
})
class BackgroundQueueRecoveryTest extends AbstractMockitoConfig {

    @Autowired
    private BackgroundProcessing backgroundProcessing;
    @Autowired
    private ScheduledRetryService scheduledRetryService;
    @Autowired
    private TransactionInfoThriftConverter transactionInfoThriftConverter;
    @Value("${wiremock.server.baseUrl}")
    private String baseUrl;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void networkFailureMovesOldCallbackBehindHealthyWork(boolean connectionRefused) throws Exception {
        stubFor(post(urlEqualTo("/slow-rpc")).willReturn(aResponse().withFixedDelay(3000)));
        var address = URI.create(baseUrl + "/slow-rpc");
        if (connectionRefused) {
            try (var socket = new ServerSocket(0)) {
                address = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/slow-rpc");
            }
        }
        var remote = new THSpawnClientBuilder().withNetworkTimeout(100)
                .withAddress(address).build(InvoicingSrv.Iface.class);
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
            task.processPending();
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
        }
    }

    @Test
    void competingWorkersSkipLockedCallbackWithoutDuplicatingAdjustmentOrBlockingOtherWork() throws Exception {
        var blocked = callback("concurrent-blocked", 24);
        var healthy = callback("concurrent-healthy", 12);
        when(invoicingClient.getPayment(blocked.getInvoiceId(), "1")).thenReturn(createInvoicePayment("1"));
        var captured = createInvoicePayment("1");
        captured.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        when(invoicingClient.getPayment(healthy.getInvoiceId(), "1")).thenReturn(captured);
        var writeStarted = new CountDownLatch(1);
        var releaseWrite = new CountDownLatch(1);
        when(invoicingClient.createPaymentAdjustment(any(), any(), any())).thenAnswer(invocation -> {
            writeStarted.countDown();
            assertThat(releaseWrite.await(30, TimeUnit.SECONDS)).as("remote write was released").isTrue();
            return getCapturedInvoicePaymentAdjustment("concurrent-adjustment",
                    providerPaymentsAdjustmentExtractor.getReason(blocked));
        });

        try (var workers = Executors.newFixedThreadPool(2)) {
            try {
                var owner = workers.submit(() -> backgroundProcessing.processProviderCallback(blocked,
                        () -> providerPaymentsService.callHgForCreateAdjustment(blocked)));
                assertThat(writeStarted.await(10, TimeUnit.SECONDS)).as("first worker holds the row lock").isTrue();

                workers.submit(() -> backgroundProcessing.processProviderCallback(blocked,
                        () -> providerPaymentsService.callHgForCreateAdjustment(blocked))).get(5, TimeUnit.SECONDS);
                workers.submit(() -> backgroundProcessing.processProviderCallback(healthy,
                        () -> providerPaymentsService.callHgForCreateAdjustment(healthy))).get(5, TimeUnit.SECONDS);

                assertThat(owner.isDone()).as("first worker is still waiting for its remote write").isFalse();
                assertThat(providerCallbackDao.get(healthy.getInvoiceId(), "1").getStatus())
                        .isEqualTo(ProviderPaymentsStatus.succeeded);
                releaseWrite.countDown();
                owner.get(5, TimeUnit.SECONDS);
            } finally {
                releaseWrite.countDown();
            }
        }

        backgroundProcessing.processProviderCallback(blocked,
                () -> providerPaymentsService.callHgForCreateAdjustment(blocked));
        verify(invoicingClient).getPayment(blocked.getInvoiceId(), "1");
        verify(invoicingClient).createPaymentAdjustment(any(), any(), any());
        assertThat(providerCallbackDao.get(blocked.getInvoiceId(), "1").getStatus())
                .isEqualTo(ProviderPaymentsStatus.succeeded);
        assertThat(providerPaymentsService.getPaymentsForHgCall(10)).isEmpty();
    }

    @Test
    void adapterFailuresRollBackEnrichmentAndOnlyNetworkErrorsScheduleRetry() throws Exception {
        var id = UUID.fromString(merchantApiMvcPerformer.createDispute("created-timeout", "1").getDisputeId());
        final var selected = disputeDao.get(id);
        disputeDao.updatePaymentRiskData(id, "original-email", "original-risk");
        when(invoicingClient.getPayment(any(), any())).thenReturn(createInvoicePayment("1"));
        when(invoicingClient.getEvents(any(), any())).thenReturn(List.of());
        when(dominantService.getProvider(any())).thenReturn(createProvider().get());
        when(dominantService.getTerminal(any())).thenReturn(createTerminal().get());
        when(dominantService.getProxy(any())).thenReturn(createProxy().get());
        stubFor(post(urlEqualTo("/slow-adapter")).willReturn(ok("not a Thrift response")
                .withHeader("Content-Type", "application/x-thrift")));
        var adapter = new THSpawnClientBuilder().withNetworkTimeout(100)
                .withAddress(URI.create(baseUrl + "/slow-adapter")).build(ProviderPaymentsServiceSrv.Iface.class);
        when(providerPaymentsThriftInterfaceBuilder.buildWoodyClient(any())).thenReturn(adapter);

        assertThatThrownBy(() -> backgroundProcessing.processDispute(selected,
                () -> createdDisputesService.callCreateDisputeRemotely(selected)))
                .isInstanceOf(WRuntimeException.class);
        assertThat(disputeDao.get(id).getNextCheckAfter()).isEqualTo(selected.getNextCheckAfter());

        stubFor(post(urlEqualTo("/slow-adapter")).willReturn(aResponse().withFixedDelay(3000)));
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
