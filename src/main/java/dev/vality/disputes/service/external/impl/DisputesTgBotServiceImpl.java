package dev.vality.disputes.service.external.impl;

import dev.vality.disputes.exception.DisputesTgBotException;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeParams;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.service.external.DisputesTgBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class DisputesTgBotServiceImpl implements DisputesTgBotService {

    public final ProviderDisputesServiceSrv.Iface providerDisputesTgBotClient;

    @Override
    public DisputeCreatedResult createDispute(DisputeParams disputeParams) {
        try {
            log.debug("Trying to create new dispute to `disputes-tg-bot` {} {}", disputeParams.getDisputeId(), disputeParams.getTransactionContext().getInvoiceId());
            var invoice = providerDisputesTgBotClient.createDispute(disputeParams);
            log.debug("Dispute has been created to `disputes-tg-bot` {} {}", disputeParams.getDisputeId(), disputeParams.getTransactionContext().getInvoiceId());
            return invoice;
        } catch (TException e2) {
            throw new DisputesTgBotException(String.format("Failed to create dispute with id: %s, %s", disputeParams.getDisputeId(), disputeParams.getTransactionContext().getInvoiceId()), e2);
        }
    }
}
