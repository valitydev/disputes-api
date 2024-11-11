package dev.vality.disputes.provider.payments.service;

import dev.vality.damsel.domain.InvoicePaymentAdjustment;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class ProviderPaymentsAdjustmentExtractor {

    public static final String PROVIDER_PAYMENT_MASK = "providerCallbackId=%s";

    public String getReason(ProviderCallback providerCallback) {
        return Optional.ofNullable(providerCallback.getApproveReason())
                .map(s -> String.format(PROVIDER_PAYMENT_MASK + ", reason=%s", providerCallback.getId(), s))
                .orElse(String.format(PROVIDER_PAYMENT_MASK, providerCallback.getId()));
    }

    public boolean isCashFlowAdjustmentByProviderPaymentsExist(InvoicePayment invoicePayment, ProviderCallback providerCallback) {
        return getInvoicePaymentAdjustmentStream(invoicePayment)
                .filter(adj -> isProviderPaymentsAdjustment(adj.getReason(), providerCallback))
                .anyMatch(adj -> adj.getState() != null && adj.getState().isSetCashFlow());
    }

    public boolean isCapturedAdjustmentByProviderPaymentsExist(InvoicePayment invoicePayment, ProviderCallback providerCallback) {
        return getInvoicePaymentAdjustmentStream(invoicePayment)
                .filter(adj -> isProviderPaymentsAdjustment(adj.getReason(), providerCallback))
                .filter(adj -> adj.getState() != null && adj.getState().isSetStatusChange())
                .filter(adj -> getTargetStatus(adj).isSetCaptured())
                .anyMatch(adj -> isProviderPaymentsAdjustment(getTargetStatus(adj).getCaptured().getReason(), providerCallback));
    }

    private Stream<InvoicePaymentAdjustment> getInvoicePaymentAdjustmentStream(InvoicePayment invoicePayment) {
        return Optional.ofNullable(invoicePayment.getAdjustments())
                .orElse(List.of())
                .stream();
    }

    private InvoicePaymentStatus getTargetStatus(InvoicePaymentAdjustment s) {
        return s.getState().getStatusChange().getScenario().getTargetStatus();
    }

    private boolean isProviderPaymentsAdjustment(String reason, ProviderCallback providerCallback) {
        return !StringUtils.isBlank(reason)
                && reason.equalsIgnoreCase(getReason(providerCallback));
    }
}
