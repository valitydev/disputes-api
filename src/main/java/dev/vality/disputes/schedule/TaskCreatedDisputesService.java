package dev.vality.disputes.schedule;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.handler.CreatedDisputeHandler;
import dev.vality.disputes.schedule.service.CreatedDisputeService;
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
public class TaskCreatedDisputesService {

    private final ExecutorService disputesThreadPool;
    private final CreatedDisputeService createdDisputeService;
    @Value("${dispute.batchSize}")
    private int batchSize;
    @Value("${dispute.isScheduleCreatedEnabled}")
    private boolean isScheduleCreatedEnabled;

    @Scheduled(fixedDelayString = "${dispute.fixedDelayCreated}")
    public void processCreated() {
        if (!isScheduleCreatedEnabled) {
            return;
        }
        log.info("Processing created disputes get started");
        try {
            var disputes = createdDisputeService.getCreatedDisputesForUpdateSkipLocked(batchSize);
            var callables = disputes.stream()
                    .map(this::handleCreated)
                    .collect(Collectors.toList());
            disputesThreadPool.invokeAll(callables);
        } catch (InterruptedException ex) {
            log.error("Received InterruptedException while thread executed report", ex);
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.error("Received exception while scheduler processed created disputes", ex);
        }
        log.info("Created disputes were processed");
    }

    private Callable<Long> handleCreated(Dispute dispute) {
        return () -> new CreatedDisputeHandler(createdDisputeService).handle(dispute);
    }
}
