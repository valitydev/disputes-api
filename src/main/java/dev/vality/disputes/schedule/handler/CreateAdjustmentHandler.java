package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.service.CreateAdjustmentsService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateAdjustmentHandler {

    private final CreateAdjustmentsService createAdjustmentsService;

    public Long handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("dispute-create-adjustment-" + dispute.getId());
        try {
            createAdjustmentsService.callHgForCreateAdjustment(dispute);
            return dispute.getId();
        } finally {
            currentThread.setName(oldName);
        }
    }
}
