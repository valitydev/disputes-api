package dev.vality.disputes.schedule.handler;

import dev.vality.disputes.schedule.core.NotificationService;
import dev.vality.swag.disputes.model.NotifyRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NotificationHandler {

    private final NotificationService notificationService;

    public String handle(NotifyRequest notifyRequest) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("notification-id-" + notifyRequest.getDisputeId() + "-" + oldName);
        try {
            notificationService.process(notifyRequest);
            return notifyRequest.getDisputeId();
        } finally {
            currentThread.setName(oldName);
        }
    }
}
