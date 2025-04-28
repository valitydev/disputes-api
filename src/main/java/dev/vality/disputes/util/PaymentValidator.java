package dev.vality.disputes.util;

import dev.vality.disputes.exception.PaymentExpiredException;
import dev.vality.disputes.security.AccessData;
import dev.vality.geck.common.util.TypeUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

public class PaymentValidator {

    public static void validatePaymentAge(AccessData accessData) {
        var payment = accessData.getPayment().getPayment();
        var invoiceId = accessData.getInvoice().getInvoice().getId();
        LocalDateTime localDateTime;
        try {
            localDateTime = TypeUtil.stringToLocalDateTime(payment.getCreatedAt());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid date format for invoice " + invoiceId + ": " + payment.getCreatedAt(), e);
        }
        var threshold = LocalDateTime.now().minusDays(30);
        if (localDateTime.isBefore(threshold)) {
            throw new PaymentExpiredException(
                    "Payment expired for invoice " + invoiceId + ": created at " + localDateTime);
        }
    }
}
