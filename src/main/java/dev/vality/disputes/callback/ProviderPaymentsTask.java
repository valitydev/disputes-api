package dev.vality.disputes.callback;

import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class ProviderPaymentsTask {

    private final ExecutorService providerPaymentsThreadPool;
    private final ProviderPaymentsService providerPaymentsService;
    @Value("${provider.payments.batchSize}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${provider.payments.fixedDelayCreateAdjustments}", initialDelayString = "${provider.payments.initialDelayCreateAdjustments}")
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
        return () -> new ProviderPaymentHandler(providerPaymentsService).handle(providerCallback);
    }
}
