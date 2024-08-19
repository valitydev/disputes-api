package dev.vality.disputes.service;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.handler.DisputeCreatedHandler;
import dev.vality.disputes.handler.DisputePendingHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl {

    private final ExecutorService disputesThreadPool;
    private final DisputeService disputeService;
    @Value("${dispute.batchSize}")
    private int batchSize;

    @Scheduled(fixedDelay = 5000)
    public void processCreated() {
        log.info("Processing created disputes get started");
        try {
            var disputes = disputeService.getCreatedDisputes(batchSize);
            log.debug("Trying to process {} created disputes", disputes.size());
            List<Callable<Long>> callables = disputes.stream()
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

    @Scheduled(fixedDelay = 5000)
    public void processPending() {
        log.info("Processing pending disputes get started");
        try {
            var disputes = disputeService.getPendingDisputes(batchSize);
            log.debug("Trying to process {} pending disputes", disputes.size());
            List<Callable<Long>> callables = disputes.stream()
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

    private Callable<Long> handleCreated(Dispute dispute) {
        return () -> new DisputeCreatedHandler(disputeService).handle(dispute);
    }

    private Callable<Long> handlePending(Dispute dispute) {
        return () -> new DisputePendingHandler(disputeService).handle(dispute);
    }

}
