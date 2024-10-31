package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.core.AdjustmentsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class AdjustmentHandler {

    private final AdjustmentsService adjustmentsService;

    public UUID handle(Dispute dispute) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("dispute-created-adjustment-id-" + dispute.getId() + "-" + oldName);
        try {
            adjustmentsService.callHgForCreateAdjustment(dispute);
            return dispute.getId();
        } catch (Throwable ex) {
            log.error("Received exception while scheduler processed callHgForCreateAdjustment", ex);
            throw ex;
        } finally {
            currentThread.setName(oldName);
        }
    }
}
