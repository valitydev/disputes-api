package dev.vality.disputes.schedule;

import dev.vality.disputes.admin.callback.CallbackNotifier;
import dev.vality.disputes.admin.management.MdcTopicProducer;
import dev.vality.disputes.schedule.core.AdjustmentsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@ConditionalOnProperty(value = "dispute.isScheduleReadyForCreateAdjustmentsEnabled", havingValue = "true")
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class AdjustmentsReadyNotificationTask {

    private final AdjustmentsService adjustmentsService;
    private final CallbackNotifier callbackNotifier;
    private final MdcTopicProducer mdcTopicProducer;

    @Scheduled(fixedDelayString = "${dispute.fixedDelayReadyForCreateAdjustments}", initialDelayString = "${dispute.initialDelayReadyForCreateAdjustments}")
    public void processPending() {
        log.debug("Processing ReadyForCreateAdjustments get started");
        var disputes = adjustmentsService.getReadyDisputesForCreateAdjustment();
        mdcTopicProducer.sendReadyForCreateAdjustments(disputes);
        callbackNotifier.sendDisputesReadyForCreateAdjustment(disputes);
        log.info("ReadyForCreateAdjustments were processed");
    }
}
