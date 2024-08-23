package dev.vality.disputes.schedule;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.handler.PendingDisputeHandler;
import dev.vality.disputes.schedule.service.PendingDisputeService;
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
public class TaskPendingDisputesService {

    private final ExecutorService disputesThreadPool;
    private final PendingDisputeService pendingDisputeService;
    @Value("${dispute.batchSize}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${dispute.fixedDelayPending}")
    public void processPending() {
        log.info("Processing pending disputes get started");
        try {
            var disputes = pendingDisputeService.getPendingDisputesForUpdateSkipLocked(batchSize);
            log.debug("Trying to process {} pending disputes", disputes.size());
            var callables = disputes.stream()
                    .map(this::handlePending)
                    .collect(Collectors.toList());
            disputesThreadPool.invokeAll(callables);
        } catch (InterruptedException ex) {
            log.error("Received InterruptedException while thread executed report", ex);
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.error("Received exception while scheduler processed pending disputes", ex);
        }
        log.info("Pending disputes were processed");
    }

    private Callable<Long> handlePending(Dispute dispute) {
        return () -> new PendingDisputeHandler(pendingDisputeService).handle(dispute);
    }
}
