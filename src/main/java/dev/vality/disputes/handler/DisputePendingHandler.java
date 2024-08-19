package dev.vality.disputes.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.service.DisputeService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DisputePendingHandler {

    private final DisputeService disputeService;

    public Long handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("dispute-creating-" + dispute.getId());
        try {
            disputeService.pendingDispute(dispute);
            return dispute.getId();
        } finally {
            currentThread.setName(oldName);
        }
    }
}
