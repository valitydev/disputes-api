package dev.vality.disputes.util;

import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.exception.CapturedPaymentException;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import lombok.experimental.UtilityClass;

import static dev.vality.disputes.constant.ErrorMessage.PAYMENT_STATUS_RESTRICTIONS;

@UtilityClass
public class PaymentStatusValidator {

    public static void checkStatus(InvoicePayment invoicePayment) {
        var invoicePaymentStatus = invoicePayment.getPayment().getStatus();
        switch (invoicePaymentStatus.getSetField()) {
            case CAPTURED -> throw new CapturedPaymentException(invoicePayment);
            case FAILED, CANCELLED -> {
            }
            default -> throw new InvoicingPaymentStatusRestrictionsException(invoicePaymentStatus);
        }
    }

    public static String getTechnicalErrorMessage(
            InvoicingPaymentStatusRestrictionsException ex) {
        if (ex.getStatus() != null) {
            return PAYMENT_STATUS_RESTRICTIONS + ": " + ex.getStatus().getSetField().getFieldName();
        }
        return PAYMENT_STATUS_RESTRICTIONS;
    }
}
