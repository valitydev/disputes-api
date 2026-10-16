package dev.vality.disputes.schedule;

import dev.vality.disputes.config.SchedulingConfig;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.core.PendingDisputesService;
import dev.vality.disputes.schedule.handler.PendingDisputeHandler;
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
@ConditionalOnProperty(value = "dispute.isSchedulePendingEnabled", havingValue = "true")
@Service
public class PendingDisputesTask {

    private final ExecutorService pendingDisputesThreadPool;
    private final PendingDisputesService pendingDisputesService;

    @Value("${dispute.batchSize}")
    private int batchSize;

    public PendingDisputesTask(@Qualifier("pendingDisputesThreadPool") ExecutorService pendingDisputesThreadPool,
                               PendingDisputesService pendingDisputesService) {
        this.pendingDisputesThreadPool = pendingDisputesThreadPool;
        this.pendingDisputesService = pendingDisputesService;
    }

    @Scheduled(
            scheduler = SchedulingConfig.PENDING_DISPUTES_SCHEDULER,
            fixedDelayString = "${dispute.fixedDelayPending}",
            initialDelayString = "${dispute.initialDelayPending}")
    public void processPending() {
        try {
            var disputes = pendingDisputesService.getPendingSkipLocked(batchSize);
            var callables = disputes.stream()
                    .map(this::handlePending)
                    .collect(Collectors.toList());
            pendingDisputesThreadPool.invokeAll(callables);
        } catch (InterruptedException ex) {
            log.error("Received InterruptedException while thread executed report", ex);
            Thread.currentThread().interrupt();
        } catch (Throwable ex) {
            log.error("Received exception while scheduler processed pending disputes", ex);
        }
    }

    private Callable<UUID> handlePending(Dispute dispute) {
        return () -> new PendingDisputeHandler(pendingDisputesService).handle(dispute);
    }
}
