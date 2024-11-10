package dev.vality.disputes.utils;

import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import lombok.experimental.UtilityClass;

@UtilityClass
@SuppressWarnings({"LineLength"})
public class PaymentStatusValidator {

    public static void checkStatus(InvoicePayment invoicePayment) {
        var invoicePaymentStatus = invoicePayment.getPayment().getStatus();
        if (!invoicePaymentStatus.isSetCancelled() && !invoicePaymentStatus.isSetFailed()) {
            throw new InvoicingPaymentStatusRestrictionsException(invoicePaymentStatus);
        }
    }

    public static String getInvoicingPaymentStatusRestrictionsErrorReason(InvoicingPaymentStatusRestrictionsException ex) {
        if (ex.getStatus() != null) {
            return ErrorMessage.PAYMENT_STATUS_RESTRICTIONS + ": " + ex.getStatus().getSetField().getFieldName();
        }
        return ErrorMessage.PAYMENT_STATUS_RESTRICTIONS;
    }
}
