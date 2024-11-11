package dev.vality.disputes.schedule.converter;

import dev.vality.damsel.domain.Currency;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import org.springframework.stereotype.Component;

@Component
public class DisputeCurrencyConverter {

    public Currency convert(Dispute dispute) {
        var currency = new Currency();
        currency.setName(dispute.getCurrencyName());
        currency.setSymbolicCode(dispute.getCurrencySymbolicCode());
        currency.setNumericCode(dispute.getCurrencyNumericCode().shortValue());
        currency.setExponent(dispute.getCurrencyExponent().shortValue());
        return currency;
    }
}
