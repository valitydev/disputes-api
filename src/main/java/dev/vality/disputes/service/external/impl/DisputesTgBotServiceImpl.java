package dev.vality.disputes.service.external.impl;

import dev.vality.disputes.admin.AdminCallbackServiceSrv;
import dev.vality.disputes.admin.Dispute;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeParams;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.service.external.DisputesTgBotService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputesTgBotServiceImpl implements DisputesTgBotService {

    public final ProviderDisputesServiceSrv.Iface providerDisputesTgBotClient;
    public final AdminCallbackServiceSrv.Iface adminCallbackDisputesTgBotClient;

    @Override
    @SneakyThrows
    public DisputeCreatedResult createDispute(DisputeParams disputeParams) {
        log.debug("Trying to call disputes-tg-bot.createDispute() {} {}", disputeParams.getDisputeId(),
                disputeParams.getTransactionContext().getInvoiceId());
        var invoice = providerDisputesTgBotClient.createDispute(disputeParams);
        log.debug("disputes-tg-bot.createDispute() has been called {} {}", disputeParams.getDisputeId(),
                disputeParams.getTransactionContext().getInvoiceId());
        return invoice;
    }

    @SneakyThrows
    @Override
    public void notify(Dispute disputeThrift) {
        log.debug("Trying to call disputes-tg-bot.notify() {}", disputeThrift);
        adminCallbackDisputesTgBotClient.notify(disputeThrift);
        log.debug("disputes-tg-bot.notify() has been called {}", disputeThrift);
    }
}
