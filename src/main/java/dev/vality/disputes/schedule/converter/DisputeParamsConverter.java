package dev.vality.disputes.schedule.converter;

import dev.vality.damsel.domain.Currency;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.Attachment;
import dev.vality.disputes.provider.Cash;
import dev.vality.disputes.provider.DisputeParams;
import dev.vality.disputes.provider.TransactionContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DisputeParamsConverter {

    public DisputeParams convert(Dispute dispute, List<Attachment> attachments, Map<String, String> terminalOptions) {
        var disputeParams = new DisputeParams();
        disputeParams.setAttachments(attachments);
        var transactionContext = new TransactionContext();
        transactionContext.setProviderTrxId(dispute.getProviderTrxId());
        transactionContext.setInvoiceId(dispute.getInvoiceId());
        transactionContext.setPaymentId(dispute.getPaymentId());
        transactionContext.setTerminalOptions(terminalOptions);
        disputeParams.setTransactionContext(transactionContext);
        if (dispute.getAmount() != null) {
            var cash = new Cash();
            cash.setAmount(dispute.getAmount());
            var currency = new Currency();
            currency.setName(dispute.getCurrencyName());
            currency.setSymbolicCode(dispute.getCurrencySymbolicCode());
            currency.setNumericCode(dispute.getCurrencyNumericCode().shortValue());
            currency.setExponent(dispute.getCurrencyExponent().shortValue());
            cash.setCurrency(currency);
            disputeParams.setCash(cash);
        }
        disputeParams.setReason(dispute.getReason());
        return disputeParams;
    }
}
