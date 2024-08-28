package dev.vality.disputes.schedule.converter;

import dev.vality.damsel.domain.Currency;
import dev.vality.disputes.DisputeContext;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DisputeContextConverter {

    public DisputeContext convert(Dispute dispute, ProviderDispute providerDispute, Map<String, String> options) {
        var disputeContext = new DisputeContext();
        disputeContext.setDisputeId(providerDispute.getProviderDisputeId());
        var currency = new Currency();
        currency.setName(dispute.getCurrencyName());
        currency.setSymbolicCode(dispute.getCurrencySymbolicCode());
        currency.setNumericCode(dispute.getCurrencyNumericCode().shortValue());
        currency.setExponent(dispute.getCurrencyExponent().shortValue());
        disputeContext.setCurrency(currency);
        disputeContext.setTerminalOptions(options);
        return disputeContext;
    }
}
