package dev.vality.disputes.schedule.converter;

import dev.vality.damsel.domain.InvoicePaymentAdjustmentCashFlow;
import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentScenario;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.service.AdjustmentExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoicePaymentCashFlowAdjustmentParamsConverter {

    private final AdjustmentExtractor adjustmentExtractor;

    public InvoicePaymentAdjustmentParams convert(Dispute dispute) {
        var captured = new InvoicePaymentCaptured();
        var reason = adjustmentExtractor.getReason(dispute);
        captured.setReason(reason);
        var params = new InvoicePaymentAdjustmentParams();
        params.setReason(reason);
        params.setScenario(getInvoicePaymentAdjustmentScenario(dispute.getChangedAmount()));
        return params;
    }

    private InvoicePaymentAdjustmentScenario getInvoicePaymentAdjustmentScenario(Long changedAmount) {
        return InvoicePaymentAdjustmentScenario.cash_flow(new InvoicePaymentAdjustmentCashFlow()
                .setNewAmount(changedAmount));
    }
}
