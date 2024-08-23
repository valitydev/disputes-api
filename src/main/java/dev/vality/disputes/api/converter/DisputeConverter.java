package dev.vality.disputes.api.converter;

import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class DisputeConverter {

    public Dispute convert(PaymentParams paymentParams, Long amount, String reason) {
        var dispute = new Dispute();
        dispute.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        dispute.setInvoiceId(paymentParams.getInvoiceId());
        dispute.setPaymentId(paymentParams.getPaymentId());
        dispute.setProviderId(paymentParams.getProviderId());
        dispute.setTerminalId(paymentParams.getTerminalId());
        dispute.setProviderTrxId(paymentParams.getProviderTrxId());
        dispute.setAmount(amount);
        dispute.setCurrencyName(paymentParams.getCurrencyName());
        dispute.setCurrencySymbolicCode(paymentParams.getCurrencySymbolicCode());
        dispute.setCurrencyNumericCode(paymentParams.getCurrencyNumericCode());
        dispute.setCurrencyExponent(paymentParams.getCurrencyExponent());
        dispute.setReason(reason);
        return dispute;
    }
}
