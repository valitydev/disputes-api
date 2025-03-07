package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.dao.model.EnrichedNotification;
import dev.vality.disputes.schedule.core.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class NotificationHandler {

    private final NotificationService notificationService;

    public UUID handle(EnrichedNotification enrichedNotification) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("notification-id-" +
                enrichedNotification.getNotification().getDisputeId() + "-" + oldName);
        try {
            notificationService.process(enrichedNotification);
            return enrichedNotification.getNotification().getDisputeId();
        } catch (Throwable ex) {
            log.error("Received exception while scheduler processed NotificationService.process", ex);
            throw ex;
        } finally {
            currentThread.setName(oldName);
        }
    }
}
