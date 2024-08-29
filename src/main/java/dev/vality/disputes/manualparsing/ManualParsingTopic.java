package dev.vality.disputes.manualparsing;

import dev.vality.disputes.Attachment;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ParameterName", "LineLength"})
public class ManualParsingTopic {

    @Value("${manual-parsing-topic.enabled}")
    private boolean enabled;

    @Transactional(propagation = Propagation.REQUIRED)
    public void sendCreated(Dispute dispute, List<Attachment> attachments) {
        if (!enabled) {
            return;
        }
        var contextMap = MDC.getCopyOfContextMap();
        contextMap.put("dispute_id", dispute.getId().toString());
        var attachmentsCollect = attachments.stream().map(Attachment::toString).collect(Collectors.joining(", "));
        contextMap.put("dispute_attachments", attachmentsCollect);
        contextMap.put("dispute_status", DisputeStatus.manual_parsing_created.name());
        contextMap.put("@severity", "warn");
        contextMap.put("message", "Manual parsing case");
        MDC.setContextMap(contextMap);
        MDC.clear(); //?? будет ли это работать и откатит ли лог при откате транзакции
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void sendSucceeded(Dispute dispute, Long changedAmount) {
        if (!enabled) {
            return;
        }
        var contextMap = MDC.getCopyOfContextMap();
        contextMap.put("dispute_id", dispute.getId().toString());
        contextMap.put("dispute_changed_amount", String.valueOf(changedAmount));
        contextMap.put("dispute_status", DisputeStatus.succeeded.name());
        contextMap.put("@severity", "warn");
        contextMap.put("message", "Manual parsing case");
        MDC.setContextMap(contextMap);
        MDC.clear(); //?? будет ли это работать и откатит ли лог при откате транзакции
    }
}
