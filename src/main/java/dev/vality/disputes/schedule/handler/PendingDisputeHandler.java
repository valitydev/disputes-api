package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.service.PendingDisputeService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PendingDisputeHandler {

    private final PendingDisputeService pendingDisputeService;

    public Long handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("dispute-pending-" + dispute.getId());
        try {
            pendingDisputeService.callPendingDisputeRemotely(dispute);
            return dispute.getId();
        } finally {
            currentThread.setName(oldName);
        }
    }
}
