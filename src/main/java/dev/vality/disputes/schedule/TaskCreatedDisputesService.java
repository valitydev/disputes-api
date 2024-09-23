package dev.vality.disputes.schedule;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.handler.CreatedDisputeHandler;
import dev.vality.disputes.schedule.service.CreatedDisputesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnProperty(value = "dispute.isScheduleCreatedEnabled", havingValue = "true")
@Service
@RequiredArgsConstructor
public class TaskCreatedDisputesService {

    private final ExecutorService disputesThreadPool;
    private final CreatedDisputesService createdDisputesService;
    @Value("${dispute.batchSize}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${dispute.fixedDelayCreated}", initialDelayString = "${dispute.initialDelayCreated}")
    public void processCreated() {
        log.debug("Processing created disputes get started");
        try {
            var disputes = createdDisputesService.getCreatedDisputesForUpdateSkipLocked(batchSize);
            var callables = disputes.stream()
                    .map(this::handleCreated)
                    .collect(Collectors.toList());
            disputesThreadPool.invokeAll(callables);
        } catch (InterruptedException ex) {
            log.error("Received InterruptedException while thread executed report", ex);
            Thread.currentThread().interrupt();
        } catch (Throwable ex) {
            log.error("Received exception while scheduler processed created disputes", ex);
        }
        log.info("Created disputes were processed");
    }

    private Callable<Long> handleCreated(Dispute dispute) {
        return () -> new CreatedDisputeHandler(createdDisputesService).handle(dispute);
    }
}
