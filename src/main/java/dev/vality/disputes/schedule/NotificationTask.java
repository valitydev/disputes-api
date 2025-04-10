package dev.vality.disputes.schedule;

import dev.vality.disputes.schedule.core.NotificationService;
import dev.vality.disputes.schedule.handler.NotificationHandler;
import dev.vality.swag.disputes.model.NotifyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    @Scheduled(fixedDelayString = "${dispute.fixedDelayNotification}", initialDelayString = "${dispute.initialDelayNotification}")
    public void processNotifications() {
        try {
            var notifications = notificationService.getNotifyRequests(batchSize);
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

    private Callable<String> handleNotification(NotifyRequest notifyRequest) {
        return () -> new NotificationHandler(notificationService).handle(notifyRequest);
    }
}
