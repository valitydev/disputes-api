package dev.vality.disputes.schedule;

import dev.vality.disputes.admin.callback.CallbackNotifier;
import dev.vality.disputes.admin.management.MdcTopicProducer;
import dev.vality.disputes.service.DisputesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@ConditionalOnProperty(value = "dispute.isScheduleForgottenDisputesNotificationsEnabled", havingValue = "true")
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class ForgottenDisputesNotificationsTask {

    private final DisputesService disputesService;
    private final CallbackNotifier callbackNotifier;
    private final MdcTopicProducer mdcTopicProducer;

    @Scheduled(cron = "${dispute.cronForgottenDisputesNotifications:-}")
    public void processForgottenDisputes() {
        var forgottenDisputes = disputesService.getForgottenDisputes();

        if (!forgottenDisputes.isEmpty()) {
            callbackNotifier.sendForgottenDisputes(forgottenDisputes);
            mdcTopicProducer.sendForgottenDisputes(forgottenDisputes);
        }
    }
}
