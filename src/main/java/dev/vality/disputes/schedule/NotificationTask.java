package dev.vality.disputes.schedule;

import dev.vality.disputes.dao.model.EnrichedNotification;
import dev.vality.disputes.schedule.core.NotificationService;
import dev.vality.disputes.schedule.handler.NotificationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnProperty(value = "dispute.isScheduleNotificationEnabled", havingValue = "true")
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class NotificationTask {

    private final ExecutorService disputesThreadPool;
    private final NotificationService notificationService;

    @Value("${dispute.batchSize}")
    private int batchSize;
    @Value("${dispute.notificationMaxAttempt}")
    private int maxAttempt;

    @Scheduled(fixedDelayString = "${dispute.fixedDelayNotification}", initialDelayString = "${dispute.initialDelayNotification}")
    public void processCreated() {
        try {
            var notifications = notificationService.getSkipLocked(batchSize, maxAttempt);
            var callables = notifications.stream()
                    .map(this::handleNotification)
                    .collect(Collectors.toList());
            disputesThreadPool.invokeAll(callables);
        } catch (InterruptedException ex) {
            log.error("Received InterruptedException while thread executed report", ex);
            Thread.currentThread().interrupt();
        } catch (Throwable ex) {
            log.error("Received exception while scheduler processed notifications", ex);
        }
    }

    private Callable<UUID> handleNotification(EnrichedNotification enrichedNotification) {
        return () -> new NotificationHandler(notificationService).handle(enrichedNotification, maxAttempt);
    }
}
