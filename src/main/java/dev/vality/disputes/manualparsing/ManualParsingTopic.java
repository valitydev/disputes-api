package dev.vality.disputes.manualparsing;

import dev.vality.disputes.Attachment;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManualParsingTopic {

    @Value("${manual-parsing-topic.enabled}")
    private boolean enabled;

    @Transactional(propagation = Propagation.REQUIRED)
    public void send(Dispute dispute, List<Attachment> attachments) {
        if (enabled) {
            sendDispute(dispute, attachments);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void sendDispute(Dispute dispute, List<Attachment> attachments) {
        log.info("!! Dispute is needed in Manual Parsing {} {}", dispute, attachments);
        var contextMap = MDC.getCopyOfContextMap();
        contextMap.put("dispute_id", dispute.getId().toString());
        contextMap.put("@severity", "warn");
        contextMap.put("message", String.format("!! Dispute is needed in Manual Parsing %s %s", dispute, attachments));
        MDC.setContextMap(contextMap);
        MDC.clear(); //?? будет ли это работать и откатит ли лог при откате транзакции
    }
}
