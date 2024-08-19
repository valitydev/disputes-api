package dev.vality.disputes.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.service.DisputeService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DisputeCreatedHandler {

    private final DisputeService disputeService;

    public Long handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("dispute-created-" + dispute.getId());
        try {
            disputeService.createDispute(dispute);
            return dispute.getId();
        } finally {
            currentThread.setName(oldName);
        }
    }
}
