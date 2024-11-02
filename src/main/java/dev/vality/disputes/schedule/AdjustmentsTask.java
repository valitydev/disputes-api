package dev.vality.disputes.schedule;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.core.AdjustmentsService;
import dev.vality.disputes.schedule.handler.AdjustmentHandler;
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
@ConditionalOnProperty(value = "dispute.isScheduleCreateAdjustmentsEnabled", havingValue = "true")
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class AdjustmentsTask {

    private final ExecutorService disputesThreadPool;
    private final AdjustmentsService adjustmentsService;
    @Value("${dispute.batchSize}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${dispute.fixedDelayCreateAdjustments}", initialDelayString = "${dispute.initialDelayCreateAdjustments}")
    public void processPending() {
        try {
            var disputes = adjustmentsService.getDisputesForHgCall(batchSize);
            var callables = disputes.stream()
                    .map(this::handleCreateAdjustment)
                    .collect(Collectors.toList());
            disputesThreadPool.invokeAll(callables);
        } catch (InterruptedException ex) {
            log.error("Received InterruptedException while thread executed report", ex);
            Thread.currentThread().interrupt();
        } catch (Throwable ex) {
            log.error("Received exception while scheduler processed create adjustments", ex);
        }
    }

    private Callable<UUID> handleCreateAdjustment(Dispute dispute) {
        return () -> new AdjustmentHandler(adjustmentsService).handle(dispute);
    }
}
