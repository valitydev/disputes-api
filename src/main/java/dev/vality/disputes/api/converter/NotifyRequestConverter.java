package dev.vality.disputes.api.converter;

import dev.vality.disputes.dao.model.EnrichedNotification;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.NotificationNotFinalStatusException;
import dev.vality.swag.disputes.model.GeneralError;
import dev.vality.swag.disputes.model.NotifyRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings({"LineLength"})
public class NotifyRequestConverter {

    public NotifyRequest convert(EnrichedNotification enrichedNotification) {
        var notification = enrichedNotification.getNotification();
        var dispute = enrichedNotification.getDispute();
        var body = new NotifyRequest()
                .disputeId(notification.getDisputeId().toString())
                .invoiceId(dispute.getInvoiceId())
                .paymentId(dispute.getPaymentId());
        body.setStatus(getStatus(dispute));
        if (!StringUtils.isBlank(dispute.getMapping())) {
            body.setReason(new GeneralError(dispute.getMapping()));
        }
        if (dispute.getChangedAmount() != null) {
            body.setChangedAmount(dispute.getChangedAmount());
        }
        return body;
    }

    private NotifyRequest.StatusEnum getStatus(Dispute dispute) {
        return switch (dispute.getStatus()) {
            case succeeded -> NotifyRequest.StatusEnum.SUCCEEDED;
            case cancelled, failed -> NotifyRequest.StatusEnum.FAILED;
            default -> throw new NotificationNotFinalStatusException(
                    String.format("Fail create NotifyRequest.StatusEnum, disputeId='%s', status'%s'", dispute.getId(), dispute.getStatus()));
        };
    }
}
