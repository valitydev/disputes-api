package dev.vality.disputes.admin.management;

import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ParameterName", "LineLength"})
public class MdcTopicProducer {

    @Value("${service.mdc-topic-producer.enabled}")
    private boolean enabled;

    public void sendCreated(Dispute dispute, DisputeStatus disputeStatus) {
        if (!enabled) {
            return;
        }
        var contextMap = MDC.getCopyOfContextMap() == null ? new HashMap<String, String>() : MDC.getCopyOfContextMap();
        contextMap.put("dispute_id", dispute.getId().toString());
        contextMap.put("dispute_status", disputeStatus.name());
        MDC.setContextMap(contextMap);
        log.warn("Manual parsing case");
        MDC.clear();
    }

    public void sendPoolingExpired(Dispute dispute) {
        if (!enabled) {
            return;
        }
        var contextMap = MDC.getCopyOfContextMap() == null ? new HashMap<String, String>() : MDC.getCopyOfContextMap();
        contextMap.put("dispute_id", dispute.getId().toString());
        contextMap.put("dispute_status", DisputeStatus.manual_pending.name());
        MDC.setContextMap(contextMap);
        log.warn("Hg pooling expired case");
        MDC.clear();
    }

    public void sendReadyForCreateAdjustments(List<Dispute> disputes) {
        if (!enabled || disputes.isEmpty()) {
            return;
        }
        var contextMap = MDC.getCopyOfContextMap() == null ? new HashMap<String, String>() : MDC.getCopyOfContextMap();
        contextMap.put("dispute_ids", disputes.stream().map(Dispute::getId).map(String::valueOf).collect(Collectors.joining(", ")));
        contextMap.put("dispute_status", DisputeStatus.create_adjustment.name());
        MDC.setContextMap(contextMap);
        log.warn("Ready for CreateAdjustments case");
        MDC.clear();
    }
}
