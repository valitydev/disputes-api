package dev.vality.disputes.merchant.converter;

import dev.vality.disputes.merchant.DisputeParams;
import dev.vality.swag.disputes.model.CreateRequest;
import dev.vality.swag.disputes.model.CreateRequestAttachmentsInner;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class CreateRequestConverter {

    public CreateRequest convert(DisputeParams disputeParams) {
        var createRequestAttachmentsInners = disputeParams.getAttachments().stream()
                .map(attachment -> new CreateRequestAttachmentsInner(attachment.getData(), attachment.getMimeType()))
                .collect(Collectors.toList());
        var createRequest = new CreateRequest(
                disputeParams.getInvoiceId(), disputeParams.getPaymentId(), createRequestAttachmentsInners);
        return createRequest;
    }
}
