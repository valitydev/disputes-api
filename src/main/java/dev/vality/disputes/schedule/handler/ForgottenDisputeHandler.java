package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.core.ForgottenDisputesService;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class ForgottenDisputeHandler {

    private final ForgottenDisputesService forgottenDisputesService;

    public UUID handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("dispute-forgotten-id-" + dispute.getId() + "-" + oldName);
        try {
            forgottenDisputesService.process(dispute);
            return dispute.getId();
        } finally {
            currentThread.setName(oldName);
        }
    }
}
