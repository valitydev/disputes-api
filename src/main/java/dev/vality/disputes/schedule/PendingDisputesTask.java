package dev.vality.disputes.schedule;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.core.PendingDisputesService;
import dev.vality.disputes.schedule.handler.PendingDisputeHandler;
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
@ConditionalOnProperty(value = "dispute.isSchedulePendingEnabled", havingValue = "true")
@Service
@RequiredArgsConstructor
public class PendingDisputesTask {

    private final ExecutorService disputesThreadPool;
    private final PendingDisputesService pendingDisputesService;

    @Value("${dispute.batchSize}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${dispute.fixedDelayPending}", initialDelayString = "${dispute.initialDelayPending}")
    public void processPending() {
        try {
            var disputes = pendingDisputesService.getPendingDisputesForUpdateSkipLocked(batchSize);
            var callables = disputes.stream()
                    .map(this::handlePending)
                    .collect(Collectors.toList());
            disputesThreadPool.invokeAll(callables);
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
