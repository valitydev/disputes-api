package dev.vality.disputes.api.converter;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.swag.disputes.model.GeneralError;
import dev.vality.swag.disputes.model.Status200Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings({"LineLength"})
public class Status200ResponseConverter {

    public Status200Response convert(Dispute dispute) {
        var body = new Status200Response();
        var status = getStatus(dispute);
        body.setStatus(status);
        if (status == Status200Response.StatusEnum.FAILED && !StringUtils.isBlank(dispute.getMapping())) {
            body.setReason(new GeneralError(dispute.getMapping()));
        }
        if (status == Status200Response.StatusEnum.SUCCEEDED && dispute.getChangedAmount() != null) {
            body.setChangedAmount(dispute.getChangedAmount());
        }
        return body;
    }

    private Status200Response.StatusEnum getStatus(Dispute dispute) {
        return switch (dispute.getStatus()) {
            case already_exist_created, manual_pending, create_adjustment, pooling_expired,
                 created, pending -> Status200Response.StatusEnum.PENDING;
            case succeeded -> Status200Response.StatusEnum.SUCCEEDED;
            case cancelled, failed -> Status200Response.StatusEnum.FAILED;
            default -> throw new NotFoundException(
                    String.format("Dispute not found, disputeId='%s'", dispute.getId()), NotFoundException.Type.DISPUTE);
        };
    }
}
