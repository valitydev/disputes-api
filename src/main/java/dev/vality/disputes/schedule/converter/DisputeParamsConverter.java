package dev.vality.disputes.schedule.converter;

import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.provider.Attachment;
import dev.vality.disputes.provider.Cash;
import dev.vality.disputes.provider.DisputeParams;
import dev.vality.disputes.provider.TransactionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class DisputeParamsConverter {

    private final DisputeCurrencyConverter disputeCurrencyConverter;

    public DisputeParams convert(Dispute dispute, List<Attachment> attachments, Map<String, String> terminalOptions, TransactionInfo transactionInfo) {
        var disputeParams = new DisputeParams();
        disputeParams.setAttachments(attachments);
        var transactionContext = new TransactionContext();
        transactionContext.setProviderTrxId(dispute.getProviderTrxId());
        transactionContext.setInvoiceId(dispute.getInvoiceId());
        transactionContext.setPaymentId(dispute.getPaymentId());
        transactionContext.setTerminalOptions(terminalOptions);
        transactionContext.setTransactionInfo(transactionInfo);
        disputeParams.setTransactionContext(transactionContext);
        if (dispute.getAmount() != null) {
            var cash = new Cash();
            cash.setAmount(dispute.getAmount());
            var currency = disputeCurrencyConverter.convert(dispute);
            cash.setCurrency(currency);
            disputeParams.setCash(cash);
        }
        disputeParams.setReason(dispute.getReason());
        disputeParams.setDisputeId(dispute.getId().toString());
        return disputeParams;
    }
}
