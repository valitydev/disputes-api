package dev.vality.disputes.api.converter;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.swag.disputes.model.GeneralError;
import dev.vality.swag.disputes.model.Status200Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class Status200ResponseConverter {

    public Status200Response convert(Dispute dispute) {
        var body = new Status200Response();
        body.setStatus(getStatus(dispute));
        if (!StringUtils.isBlank(dispute.getMapping())) {
            body.setReason(new GeneralError(dispute.getMapping()));
        }
        if (dispute.getChangedAmount() != null) {
            body.setChangedAmount(dispute.getChangedAmount());
        }
        return body;
    }

    private Status200Response.StatusEnum getStatus(Dispute dispute) {
        return switch (dispute.getStatus()) {
            case created, pending, manual_created, manual_pending, create_adjustment, already_exist_created ->
                    Status200Response.StatusEnum.PENDING;
            case succeeded -> Status200Response.StatusEnum.SUCCEEDED;
            case cancelled, failed -> Status200Response.StatusEnum.FAILED;
        };
    }
}
