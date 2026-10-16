package dev.vality.disputes.provider.payments.schedule;

import dev.vality.disputes.config.SchedulingConfig;
import dev.vality.disputes.retry.BackgroundProcessing;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.handler.ProviderPaymentHandler;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnProperty(value = "provider.payments.isScheduleCreateAdjustmentsEnabled", havingValue = "true")
@Service
public class ProviderPaymentsTask {

    private final ExecutorService providerPaymentsThreadPool;
    private final ProviderPaymentsService providerPaymentsService;
    private final BackgroundProcessing backgroundProcessing;

    @Value("${provider.payments.batchSize}")
    private int batchSize;

    public ProviderPaymentsTask(@Qualifier("providerPaymentsThreadPool") ExecutorService providerPaymentsThreadPool,
                                 ProviderPaymentsService providerPaymentsService,
                                 BackgroundProcessing backgroundProcessing) {
        this.providerPaymentsThreadPool = providerPaymentsThreadPool;
        this.providerPaymentsService = providerPaymentsService;
        this.backgroundProcessing = backgroundProcessing;
    }

    @Scheduled(
            scheduler = SchedulingConfig.PROVIDER_PAYMENTS_ADJUSTMENT_SCHEDULER,
            fixedDelayString = "${provider.payments.fixedDelayCreateAdjustments}",
            initialDelayString = "${provider.payments.initialDelayCreateAdjustments}")
    public void processPending() {
        try {
            var paymentsForHgCall = providerPaymentsService.getPaymentsForHgCall(batchSize);
            var callables = paymentsForHgCall.stream()
                    .map(this::handleProviderPaymentsCreateAdjustment)
                    .collect(Collectors.toList());
            providerPaymentsThreadPool.invokeAll(callables);
        } catch (InterruptedException ex) {
            log.error("Received InterruptedException while thread executed report", ex);
            Thread.currentThread().interrupt();
        } catch (Throwable ex) {
            log.error("Received exception while scheduler processed ProviderPayments create adjustments", ex);
        }
    }

    private Callable<UUID> handleProviderPaymentsCreateAdjustment(ProviderCallback providerCallback) {
        return () -> backgroundProcessing.processProviderCallback(providerCallback,
                () -> new ProviderPaymentHandler(providerPaymentsService).handle(providerCallback));
    }
}
