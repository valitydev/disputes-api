package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.service.CreatedDisputesService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreatedDisputeHandler {

    private final CreatedDisputesService createdDisputesService;

    public Long handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("dispute-created-" + dispute.getId());
        try {
            createdDisputesService.callCreateDisputeRemotely(dispute);
            return dispute.getId();
        } finally {
            currentThread.setName(oldName);
        }
    }
}
