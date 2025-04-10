package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.core.CreatedDisputesService;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class CreatedDisputeHandler {

    private final CreatedDisputesService createdDisputesService;

    public UUID handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("dispute-created-id-" + dispute.getId() + "-" + oldName);
        try {
            createdDisputesService.callCreateDisputeRemotely(dispute);
            return dispute.getId();
        } finally {
            currentThread.setName(oldName);
        }
    }
}
