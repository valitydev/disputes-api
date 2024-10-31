package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.InvoicePaymentAdjustment;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength"})
public class AdjustmentExtractor {

    public static final String DISPUTE_MASK = "disputeId=%s";

    public String getReason(Dispute dispute) {
        return Optional.ofNullable(dispute.getReason())
                .map(s -> String.format(DISPUTE_MASK + ", reason=%s", dispute.getId(), s))
                .orElse(String.format(DISPUTE_MASK, dispute.getId()));
    }

    public boolean isCashFlowAdjustmentByDisputeExist(InvoicePayment invoicePayment, Dispute dispute) {
        return getInvoicePaymentAdjustmentStream(invoicePayment)
                .filter(adj -> isDisputesAdjustment(adj.getReason(), dispute))
                .anyMatch(adj -> adj.getState() != null && adj.getState().isSetCashFlow());
    }

    public boolean isCapturedAdjustmentByDisputeExist(InvoicePayment invoicePayment, Dispute dispute) {
        return getInvoicePaymentAdjustmentStream(invoicePayment)
                .filter(adj -> isDisputesAdjustment(adj.getReason(), dispute))
                .filter(adj -> adj.getState() != null && adj.getState().isSetStatusChange())
                .filter(adj -> getTargetStatus(adj).isSetCaptured())
                .anyMatch(adj -> isDisputesAdjustment(getTargetStatus(adj).getCaptured().getReason(), dispute));
    }

    private Stream<InvoicePaymentAdjustment> getInvoicePaymentAdjustmentStream(InvoicePayment invoicePayment) {
        return Optional.ofNullable(invoicePayment.getAdjustments())
                .orElse(List.of())
                .stream();
    }

    private InvoicePaymentStatus getTargetStatus(InvoicePaymentAdjustment s) {
        return s.getState().getStatusChange().getScenario().getTargetStatus();
    }

    private boolean isDisputesAdjustment(String reason, Dispute dispute) {
        return !StringUtils.isBlank(reason)
                && reason.equalsIgnoreCase(getReason(dispute));
    }
}
