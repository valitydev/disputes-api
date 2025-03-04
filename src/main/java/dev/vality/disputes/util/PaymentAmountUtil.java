package dev.vality.disputes.util;

import dev.vality.damsel.domain.Cash;
import dev.vality.damsel.domain.InvoicePayment;
import lombok.experimental.UtilityClass;

import java.util.Optional;

@UtilityClass
public class PaymentAmountUtil {

    public static Long getChangedAmount(InvoicePayment payment) {
        return Optional.ofNullable(payment.getChangedCost())
                .map(Cash::getAmount)
                .filter(a -> payment.getCost().getAmount() != a)
                .orElse(null);
    }
}
