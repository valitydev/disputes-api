package dev.vality.disputes.util;

import dev.vality.damsel.domain.Cash;
import dev.vality.damsel.domain.InvoicePayment;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.provider.payments.PaymentStatusResult;
import lombok.experimental.UtilityClass;

import java.util.Objects;
import java.util.Optional;

@UtilityClass
public class ChangedAmountResolver {

    public static Long fromInvoicePayment(InvoicePayment payment) {
        return getChangedCostAmount(payment)
                .or(() -> getCapturedCostAmount(payment))
                .orElse(null);
    }

    public static Long fromDisputeStatusResult(long amount, DisputeStatusResult result) {
        return changedAmount(amount, result.getStatusSuccess().getChangedAmount());
    }

    public static Long fromPaymentStatusResult(long amount, PaymentStatusResult result) {
        return changedAmount(amount, result.getChangedAmount());
    }

    public static boolean shouldCreateCashFlowAdjustment(
            Long amount,
            Long changedAmount,
            boolean cashFlowAdjustmentExists) {
        return !cashFlowAdjustmentExists
                && amount != null
                && changedAmount != null
                && !Objects.equals(amount, changedAmount);
    }

    private static Long changedAmount(long amount, Optional<Long> changedAmount) {
        return changedAmount
                .filter(value -> value != amount)
                .orElse(null);
    }

    private static Optional<Long> getChangedCostAmount(InvoicePayment payment) {
        return Optional.ofNullable(payment.getChangedCost())
                .map(Cash::getAmount)
                .filter(amount -> !Objects.equals(payment.getCost().getAmount(), amount));
    }

    private static Optional<Long> getCapturedCostAmount(InvoicePayment payment) {
        return Optional.of(payment.getStatus())
                .filter(status -> status.isSetCaptured() && status.getCaptured().isSetCost())
                .map(status -> status.getCaptured().getCost().getAmount())
                .filter(amount -> !Objects.equals(payment.getCost().getAmount(), amount));
    }
}
