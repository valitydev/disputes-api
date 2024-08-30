package dev.vality.disputes.schedule.converter;

import dev.vality.damsel.domain.*;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentScenario;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class InvoicePaymentAdjustmentParamsConverter {

    public static final String DISPUTE_MASK = "disputeId=%s";

    public InvoicePaymentAdjustmentParams convert(Dispute dispute) {
        var captured = new InvoicePaymentCaptured();
        var reason = getReason(dispute);
        captured.setReason(reason);
        var changedAmount = dispute.getChangedAmount();
        if (changedAmount != null) {
            var cost = new Cash(changedAmount, new CurrencyRef(dispute.getCurrencySymbolicCode()));
            captured.setCost(cost);
        }
        var params = new InvoicePaymentAdjustmentParams();
        params.setReason(reason);
        params.setScenario(getInvoicePaymentAdjustmentScenario(captured));
        return params;
    }

    private String getReason(Dispute dispute) {
        return Optional.ofNullable(dispute.getReason())
                .map(s -> String.format(DISPUTE_MASK + ", reason=%s", dispute.getId(), s))
                .orElse(String.format(DISPUTE_MASK, dispute.getId()));
    }

    private InvoicePaymentAdjustmentScenario getInvoicePaymentAdjustmentScenario(InvoicePaymentCaptured captured) {
        return InvoicePaymentAdjustmentScenario.status_change(new InvoicePaymentAdjustmentStatusChange(
                InvoicePaymentStatus.captured(captured)));
    }
}
