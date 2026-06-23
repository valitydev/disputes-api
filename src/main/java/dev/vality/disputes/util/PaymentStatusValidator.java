package dev.vality.disputes.util;

import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.exception.CapturedPaymentException;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import lombok.experimental.UtilityClass;

import static dev.vality.disputes.constant.ErrorMessage.PAYMENT_STATUS_RESTRICTIONS;

@UtilityClass
public class PaymentStatusValidator {

    public enum StatusAction {
        CONTINUE,
        WAIT,
        SUCCEEDED,
        CAPTURED,
        FAILED
    }

    public static void checkStatus(InvoicePayment invoicePayment) {
        checkStatus(invoicePayment, false);
    }

    public static void checkStatus(InvoicePayment invoicePayment, boolean allowPending) {
        var invoicePaymentStatus = invoicePayment.getPayment().getStatus();
        switch (invoicePaymentStatus.getSetField()) {
            case CAPTURED -> throw new CapturedPaymentException(invoicePayment);
            case FAILED, CANCELLED -> {
            }
            case PENDING -> {
                if (!allowPending) {
                    throw new InvoicingPaymentStatusRestrictionsException(invoicePaymentStatus);
                }
            }
            default -> throw new InvoicingPaymentStatusRestrictionsException(invoicePaymentStatus);
        }
    }

    public static StatusAction getDisputeLifecycleAction(InvoicePayment invoicePayment) {
        var invoicePaymentStatus = invoicePayment.getPayment().getStatus();
        return switch (invoicePaymentStatus.getSetField()) {
            case CAPTURED -> StatusAction.SUCCEEDED;
            case FAILED, CANCELLED, PENDING -> StatusAction.CONTINUE;
            default -> StatusAction.FAILED;
        };
    }

    public static StatusAction getAdjustmentLifecycleAction(InvoicePayment invoicePayment) {
        var invoicePaymentStatus = invoicePayment.getPayment().getStatus();
        return switch (invoicePaymentStatus.getSetField()) {
            case PENDING -> StatusAction.WAIT;
            case CAPTURED -> StatusAction.CAPTURED;
            case FAILED, CANCELLED -> StatusAction.CONTINUE;
            default -> StatusAction.FAILED;
        };
    }

    public static String getTechnicalErrorMessage(InvoicePayment invoicePayment) {
        return PAYMENT_STATUS_RESTRICTIONS + ": " + invoicePayment.getPayment().getStatus()
                .getSetField()
                .getFieldName();
    }

    public static String getTechnicalErrorMessage(
            InvoicingPaymentStatusRestrictionsException ex) {
        if (ex.getStatus() != null) {
            return PAYMENT_STATUS_RESTRICTIONS + ": " + ex.getStatus().getSetField().getFieldName();
        }
        return PAYMENT_STATUS_RESTRICTIONS;
    }
}
