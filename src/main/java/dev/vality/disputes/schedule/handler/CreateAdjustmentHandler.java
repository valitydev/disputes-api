package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.service.CreateAdjustmentsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class CreateAdjustmentHandler {

    private final CreateAdjustmentsService createAdjustmentsService;

    public UUID handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("dispute-created-adjustment-id-" + dispute.getId() + "-" + oldName);
        try {
            createAdjustmentsService.callHgForCreateAdjustment(dispute);
            return dispute.getId();
        } catch (Throwable ex) {
            log.error("Received exception while scheduler processed callHgForCreateAdjustment", ex);
            throw ex;
        } finally {
            currentThread.setName(oldName);
        }
    }
}
