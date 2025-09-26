package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.core.CreatedDisputesService;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import static dev.vality.disputes.util.ThreadFormatter.buildThreadName;

@RequiredArgsConstructor
public class CreatedDisputeHandler {

    private final CreatedDisputesService createdDisputesService;

    public UUID handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName(buildThreadName("created", oldName, dispute));
        try {
            createdDisputesService.callCreateDisputeRemotely(dispute);
            return dispute.getId();
        } finally {
            currentThread.setName(oldName);
        }
    }
}
