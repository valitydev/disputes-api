package dev.vality.disputes.utils;

import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PaymentStatusValidator {

    public static void checkStatus(InvoicePayment invoicePayment) {
        var invoicePaymentStatus = invoicePayment.getPayment().getStatus();
        if (!invoicePaymentStatus.isSetCancelled() && !invoicePaymentStatus.isSetFailed()) {
            throw new InvoicingPaymentStatusRestrictionsException();
        }
    }
}
