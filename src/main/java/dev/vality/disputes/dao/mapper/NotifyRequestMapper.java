package dev.vality.disputes.dao.mapper;

import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.swag.disputes.model.GeneralError;
import dev.vality.swag.disputes.model.NotifyRequest;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class NotifyRequestMapper implements RowMapper<NotifyRequest> {

    private static final Map<DisputeStatus, NotifyRequest.StatusEnum> STATUS_MAP = Map.of(
            DisputeStatus.succeeded, NotifyRequest.StatusEnum.SUCCEEDED,
            DisputeStatus.failed, NotifyRequest.StatusEnum.FAILED,
            DisputeStatus.cancelled, NotifyRequest.StatusEnum.FAILED
    );

    @Override
    public NotifyRequest mapRow(ResultSet rs, int i) throws SQLException {
        var request = new NotifyRequest();
        request.setDisputeId(rs.getObject("dispute_id", UUID.class).toString());
        request.setInvoiceId(rs.getString("invoice_id"));
        request.setPaymentId(rs.getString("payment_id"));
        var status = STATUS_MAP.get(DisputeStatus.valueOf(rs.getString("dispute_status")));
        request.setStatus(status);
        var mapping = rs.getString("mapping");
        if (mapping != null && !mapping.isBlank()) {
            request.setReason(new GeneralError(mapping));
        }
        var changedAmount = rs.getObject("changed_amount", Long.class);
        if (changedAmount != null) {
            request.setChangedAmount(changedAmount);
        }
        return request;
    }
}
