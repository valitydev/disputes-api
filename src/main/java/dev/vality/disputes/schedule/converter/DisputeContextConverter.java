package dev.vality.disputes.schedule.converter;

import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.provider.DisputeContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class DisputeContextConverter {

    private final DisputeCurrencyConverter disputeCurrencyConverter;

    public DisputeContext convert(Dispute dispute, ProviderDispute providerDispute, Map<String, String> options, TransactionInfo transactionInfo) {
        var disputeContext = new DisputeContext();
        disputeContext.setProviderDisputeId(providerDispute.getProviderDisputeId());
        var currency = disputeCurrencyConverter.convert(dispute);
        disputeContext.setCurrency(currency);
        disputeContext.setTerminalOptions(options);
        disputeContext.setTransactionInfo(transactionInfo);
        return disputeContext;
    }
}
