package dev.vality.disputes.merchant.converter;

import dev.vality.disputes.merchant.DisputeParams;
import dev.vality.swag.disputes.model.CreateRequest;
import dev.vality.swag.disputes.model.CreateRequestAttachmentsInner;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@SuppressWarnings({"LineLength"})
public class CreateRequestConverter {

    public CreateRequest convert(DisputeParams disputeParams) {
        return new CreateRequest(
                disputeParams.getInvoiceId(),
                disputeParams.getPaymentId(),
                disputeParams.getAttachments().stream()
                        .map(attachment -> new CreateRequestAttachmentsInner(attachment.getData(), attachment.getMimeType()))
                        .collect(Collectors.toList()))
                .notificationUrl(disputeParams.getNotificationUrl().orElse(null));
    }
}
