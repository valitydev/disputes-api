package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.core.PendingDisputesService;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import static dev.vality.disputes.util.ThreadFormatter.buildThreadName;

@RequiredArgsConstructor
public class PendingDisputeHandler {

    private final PendingDisputesService pendingDisputesService;

    public UUID handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName(buildThreadName("pending", oldName, dispute));
        try {
            pendingDisputesService.callPendingDisputeRemotely(dispute);
            return dispute.getId();
        } finally {
            currentThread.setName(oldName);
        }
    }
}
