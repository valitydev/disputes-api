package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.service.CreatedDisputeService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreatedDisputeHandler {

    private final CreatedDisputeService createdDisputeService;

    public Long handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("dispute-created-" + dispute.getId());
        try {
            createdDisputeService.callCreateDisputeRemotely(dispute);
            return dispute.getId();
        } finally {
            currentThread.setName(oldName);
        }
    }
}
