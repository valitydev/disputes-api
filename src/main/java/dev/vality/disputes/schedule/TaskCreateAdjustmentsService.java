package dev.vality.disputes.schedule;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.handler.CreateAdjustmentHandler;
import dev.vality.disputes.schedule.service.CreateAdjustmentsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCreateAdjustmentsService {

    private final ExecutorService disputesThreadPool;
    private final CreateAdjustmentsService createAdjustmentsService;
    @Value("${dispute.batchSize}")
    private int batchSize;
    @Value("${dispute.isScheduleCreateAdjustmentsEnabled}")
    private boolean isScheduleCreateAdjustmentsEnabled;

    @Scheduled(fixedDelayString = "${dispute.fixedDelayCreateAdjustments}")
    public void processPending() {
        if (!isScheduleCreateAdjustmentsEnabled) {
            return;
        }
        log.info("Processing create adjustments get started");
        try {
            var disputes = createAdjustmentsService.getDisputesForHgCall(batchSize);
            var callables = disputes.stream()
                    .map(this::handleCreateAdjustment)
                    .collect(Collectors.toList());
            disputesThreadPool.invokeAll(callables);
        } catch (InterruptedException ex) {
            log.error("Received InterruptedException while thread executed report", ex);
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.error("Received exception while scheduler processed create adjustments", ex);
        }
        log.info("Create adjustments were processed");
    }

    private Callable<Long> handleCreateAdjustment(Dispute dispute) {
        return () -> new CreateAdjustmentHandler(createAdjustmentsService).handle(dispute);
    }
}
