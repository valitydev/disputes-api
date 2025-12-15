package dev.vality.disputes.util;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.provider.payments.ProviderPaymentsCallbackParams;
import dev.vality.swag.disputes.model.NotifyRequest;

public class ThreadFormatter {

    public static String buildThreadName(String prefix, String oldName, Dispute dispute) {
        return String.format("%s-%s-%s-%s.%s", prefix, oldName, dispute.getId(), dispute.getInvoiceId(),
                dispute.getPaymentId());
    }

    public static String buildThreadName(String prefix, String oldName, NotifyRequest notifyRequest) {
        return String.format("%s-%s-%s-%s.%s", prefix, oldName, notifyRequest.getDisputeId(),
                notifyRequest.getInvoiceId(), notifyRequest.getPaymentId());
    }

    public static String buildThreadName(String prefix, String oldName, ProviderPaymentsCallbackParams callback) {
        return String.format("%s-%s-%s.%s", prefix, oldName, callback.getInvoiceId().orElse(null),
                callback.getPaymentId().orElse(null));
    }
}

