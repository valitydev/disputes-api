package dev.vality.disputes.admin.callback;

import dev.vality.disputes.admin.converter.DisputeThriftConverter;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.service.external.DisputesTgBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackNotifierImpl implements CallbackNotifier {

    private final DisputeThriftConverter disputeThriftConverter;
    private final DisputesTgBotService disputesTgBotService;

    @Value("${service.disputes-tg-bot.admin.enabled}")
    private boolean tgBotEnabled;

    @Override
    @Async("disputesAsyncServiceExecutor")
    public void notify(Dispute dispute) {
        if (!tgBotEnabled) {
            return;
        }
        try {
            var disputeThrift = disputeThriftConverter.convert(dispute, false);
            disputesTgBotService.notify(disputeThrift);
        } catch (Throwable ex) {
            log.warn("Failed to to call disputes-tg-bot.notify()", ex);
        }
    }
}
