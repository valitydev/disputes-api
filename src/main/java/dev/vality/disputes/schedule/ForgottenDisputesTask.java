package dev.vality.disputes.schedule;

import dev.vality.disputes.admin.callback.CallbackNotifier;
import dev.vality.disputes.admin.management.MdcTopicProducer;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.core.ForgottenDisputesService;
import dev.vality.disputes.schedule.handler.ForgottenDisputeHandler;
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
@ConditionalOnProperty(value = "dispute.isScheduleForgottenEnabled", havingValue = "true")
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class ForgottenDisputesTask {

    private final ForgottenDisputesService forgottenDisputesService;
    private final CallbackNotifier callbackNotifier;
    private final MdcTopicProducer mdcTopicProducer;
    private final ExecutorService disputesThreadPool;

    @Value("${dispute.batchSize}")
    private int batchSize;

    @Value("${dispute.isCronForgottenDisputesNotificationsEnabled}")
    private boolean isCronForgottenDisputesNotificationsEnabled;

    @Scheduled(fixedDelayString = "${dispute.fixedDelayForgotten}", initialDelayString = "${dispute.initialDelayForgotten}")
    public void processForgottenDisputes() {
        var disputes = forgottenDisputesService.getForgottenSkipLocked(batchSize);
        try {
            var callables = disputes.stream()
                    .map(this::handleForgotten)
                    .collect(Collectors.toList());
            disputesThreadPool.invokeAll(callables);
        } catch (InterruptedException ex) {
            log.error("Received InterruptedException while thread executed report", ex);
            Thread.currentThread().interrupt();
        } catch (Throwable ex) {
            log.error("Received exception while scheduler processed Forgotten disputes", ex);
        }
    }

    @Scheduled(cron = "${dispute.cronForgottenDisputesNotifications:-}")
    public void cronForgottenDisputes() {
        if (isCronForgottenDisputesNotificationsEnabled) {
            var disputes = forgottenDisputesService.getForgottenSkipLocked(Integer.MAX_VALUE);
            if (!disputes.isEmpty()) {
                callbackNotifier.sendForgottenDisputes(disputes);
                mdcTopicProducer.sendForgottenDisputes(disputes);
            }
        }
    }

    private Callable<UUID> handleForgotten(Dispute dispute) {
        return () -> new ForgottenDisputeHandler(forgottenDisputesService).handle(dispute);
    }
}
