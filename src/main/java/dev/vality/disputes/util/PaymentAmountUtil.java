package dev.vality.disputes.util;

import dev.vality.damsel.domain.Cash;
import dev.vality.damsel.domain.InvoicePayment;
import lombok.experimental.UtilityClass;

import java.util.Optional;

@UtilityClass
public class PaymentAmountUtil {

    public static Long getChangedAmount(InvoicePayment payment) {
        return getChangedCostAmount(payment)
                .or(() -> getCapturedCostAmount(payment))
                .orElse(null);
    }

    private static Optional<Long> getChangedCostAmount(InvoicePayment payment) {
        return Optional.ofNullable(payment.getChangedCost())
                .map(Cash::getAmount)
                .filter(a -> payment.getCost().getAmount() != a);
    }

    private static Optional<Long> getCapturedCostAmount(InvoicePayment payment) {
        return Optional.of(payment.getStatus())
                .filter(status -> status.isSetCaptured() && status.getCaptured().isSetCost())
                .map(status -> status.getCaptured().getCost().getAmount());
    }
}
