package dev.vality.disputes.schedule;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.handler.CreateAdjustmentHandler;
import dev.vality.disputes.schedule.service.CreateAdjustmentsService;
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
public class TaskCreateAdjustmentsService {

    private final ExecutorService disputesThreadPool;
    private final CreateAdjustmentsService createAdjustmentsService;
    @Value("${dispute.batchSize}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${dispute.fixedDelayCreateAdjustments}", initialDelayString = "${dispute.initialDelayCreateAdjustments}")
    public void processPending() {
        log.debug("Processing create adjustments get started");
        try {
            var disputes = createAdjustmentsService.getDisputesForHgCall(batchSize);
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
        log.info("Create adjustments were processed");
    }

    private Callable<UUID> handleCreateAdjustment(Dispute dispute) {
        return () -> new CreateAdjustmentHandler(createAdjustmentsService).handle(dispute);
    }
}
