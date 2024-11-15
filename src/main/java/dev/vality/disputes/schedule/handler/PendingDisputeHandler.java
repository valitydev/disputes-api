package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.core.PendingDisputesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class PendingDisputeHandler {

    private final PendingDisputesService pendingDisputesService;

    public UUID handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("dispute-pending-id-" + dispute.getId() + "-" + oldName);
        try {
            pendingDisputesService.callPendingDisputeRemotely(dispute);
            return dispute.getId();
        } catch (Throwable ex) {
            log.warn("Received exception while scheduler processed callPendingDisputeRemotely", ex);
            throw ex;
        } finally {
            currentThread.setName(oldName);
        }
    }
}
