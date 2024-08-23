package dev.vality.disputes.schedule.converter;

import dev.vality.damsel.domain.*;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentScenario;
import dev.vality.disputes.DisputeStatusResult;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class InvoicePaymentAdjustmentParamsConverter {

    public static final String DISPUTE_MASK = "disputeId=%s";

    public InvoicePaymentAdjustmentParams convert(Dispute dispute, DisputeStatusResult result) {
        var captured = new InvoicePaymentCaptured();
        var reason = Optional.ofNullable(dispute.getReason())
                .map(s -> String.format(DISPUTE_MASK + ", reason=%s", dispute.getId(), s))
                .orElse(String.format(DISPUTE_MASK, dispute.getId()));
        captured.setReason(reason);
        var changedAmount = result.getStatusSuccess().getChangedAmount();
        if (changedAmount.isPresent()) {
            var cost = new Cash(changedAmount.get(), new CurrencyRef(dispute.getCurrencySymbolicCode()));
            captured.setCost(cost);
        }
        var params = new InvoicePaymentAdjustmentParams();
        params.setReason(reason);
        params.setScenario(getInvoicePaymentAdjustmentScenario(captured));
        return params;
    }

    private InvoicePaymentAdjustmentScenario getInvoicePaymentAdjustmentScenario(InvoicePaymentCaptured captured) {
        return InvoicePaymentAdjustmentScenario.status_change(new InvoicePaymentAdjustmentStatusChange(
                InvoicePaymentStatus.captured(captured)));
    }
}
