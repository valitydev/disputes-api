package dev.vality.disputes.schedule;

import dev.vality.disputes.config.SchedulingConfig;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.core.CreatedDisputesService;
import dev.vality.disputes.schedule.handler.CreatedDisputeHandler;
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
@ConditionalOnProperty(value = "dispute.isScheduleCreatedEnabled", havingValue = "true")
@Service
public class CreatedDisputesTask {

    private final ExecutorService createdDisputesThreadPool;
    private final CreatedDisputesService createdDisputesService;

    @Value("${dispute.batchSize}")
    private int batchSize;

    public CreatedDisputesTask(@Qualifier("createdDisputesThreadPool") ExecutorService createdDisputesThreadPool,
                               CreatedDisputesService createdDisputesService) {
        this.createdDisputesThreadPool = createdDisputesThreadPool;
        this.createdDisputesService = createdDisputesService;
    }

    @Scheduled(
            scheduler = SchedulingConfig.CREATED_DISPUTES_SCHEDULER,
            fixedDelayString = "${dispute.fixedDelayCreated}",
            initialDelayString = "${dispute.initialDelayCreated}")
    public void processCreated() {
        try {
            var disputes = createdDisputesService.getCreatedSkipLocked(batchSize);
            var callables = disputes.stream()
                    .map(this::handleCreated)
                    .collect(Collectors.toList());
            createdDisputesThreadPool.invokeAll(callables);
        } catch (InterruptedException ex) {
            log.error("Received InterruptedException while thread executed report", ex);
            Thread.currentThread().interrupt();
        } catch (Throwable ex) {
            log.error("Received exception while scheduler processed created disputes", ex);
        }
    }

    private Callable<UUID> handleCreated(Dispute dispute) {
        return () -> new CreatedDisputeHandler(createdDisputesService).handle(dispute);
    }
}
