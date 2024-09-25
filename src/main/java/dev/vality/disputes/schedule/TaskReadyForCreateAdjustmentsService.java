package dev.vality.disputes.schedule;

import dev.vality.disputes.manualparsing.ManualParsingTopic;
import dev.vality.disputes.schedule.service.CreateAdjustmentsService;
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
public class TaskReadyForCreateAdjustmentsService {

    private final CreateAdjustmentsService createAdjustmentsService;
    private final ManualParsingTopic manualParsingTopic;

    @Scheduled(fixedDelayString = "${dispute.fixedDelayReadyForCreateAdjustments}", initialDelayString = "${dispute.initialDelayReadyForCreateAdjustments}")
    public void processPending() {
        log.debug("Processing ReadyForCreateAdjustments get started");
        var disputes = createAdjustmentsService.getReadyDisputesForCreateAdjustment();
        manualParsingTopic.sendReadyForCreateAdjustments(disputes);
        log.info("ReadyForCreateAdjustments were processed");
    }
}
