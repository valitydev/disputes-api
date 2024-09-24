package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.Cash;
import dev.vality.damsel.domain.InvoicePaymentAdjustment;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static dev.vality.disputes.schedule.converter.InvoicePaymentAdjustmentParamsConverter.DISPUTE_MASK;

@Component
@SuppressWarnings({"ParameterName", "LineLength"})
public class AdjustmentExtractor {

    public Optional<InvoicePaymentAdjustment> searchAdjustmentByDispute(InvoicePayment invoicePayment, Dispute dispute) {
        return getInvoicePaymentAdjustmentStream(invoicePayment)
                .filter(adj -> adj.getReason() != null)
                .filter(adj -> isDisputesAdjustment(adj.getReason(), dispute))
                .findFirst()
                .or(() -> getInvoicePaymentAdjustmentStream(invoicePayment)
                        .filter(s -> s.getState() != null)
                        .filter(s -> s.getState().isSetStatusChange())
                        .filter(s -> getTargetStatus(s).isSetCaptured())
                        .filter(s -> getTargetStatus(s).getCaptured().getReason() != null)
                        .filter(s -> isDisputesAdjustment(getTargetStatus(s).getCaptured().getReason(), dispute))
                        .findFirst());
    }

    public Long getChangedAmount(@Nonnull InvoicePaymentAdjustment invoicePaymentAdjustment, Long changedAmount) {
        return Optional.of(invoicePaymentAdjustment)
                .map(s -> getTargetStatus(s).getCaptured().getCost())
                .map(Cash::getAmount)
                .or(() -> Optional.ofNullable(changedAmount))
                .orElse(null);
    }

    private Stream<InvoicePaymentAdjustment> getInvoicePaymentAdjustmentStream(InvoicePayment invoicePayment) {
        return Optional.ofNullable(invoicePayment.getAdjustments()).orElse(List.of()).stream();
    }

    private InvoicePaymentStatus getTargetStatus(InvoicePaymentAdjustment s) {
        return s.getState().getStatusChange().getScenario().getTargetStatus();
    }

    private boolean isDisputesAdjustment(String reason, Dispute dispute) {
        return !StringUtils.isBlank(reason) && reason.contains(String.format(DISPUTE_MASK, dispute.getId()));
    }
}
