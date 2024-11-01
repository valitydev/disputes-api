package dev.vality.disputes.schedule;

import dev.vality.disputes.admin.callback.CallbackNotifier;
import dev.vality.disputes.admin.management.MdcTopicProducer;
import dev.vality.disputes.dao.DisputeDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@ConditionalOnProperty(value = "dispute.isScheduleForgottenDisputesNotificationsEnabled", havingValue = "true")
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class ForgottenDisputesNotificationsTask {

    private final DisputeDao disputeDao;
    private final CallbackNotifier callbackNotifier;
    private final MdcTopicProducer mdcTopicProducer;

    @Scheduled(cron = "${dispute.cronForgottenDisputesNotifications:-}")
    public void processForgottenDisputes() {
        log.debug("Processing ReadyForCreateAdjustments get started");
        var forgottenDisputes = disputeDao.getForgottenDisputes();
        if (!forgottenDisputes.isEmpty()) {
            callbackNotifier.sendForgottenDisputes(forgottenDisputes);
            mdcTopicProducer.sendForgottenDisputes(forgottenDisputes);
        }
        log.info("ReadyForCreateAdjustments were processed");
    }
}
