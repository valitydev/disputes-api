package dev.vality.disputes.dao.mapper;

import dev.vality.disputes.dao.model.EnrichedNotification;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.Notification;
import dev.vality.mapper.RecordRowMapper;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static dev.vality.disputes.domain.tables.Dispute.DISPUTE;
import static dev.vality.disputes.domain.tables.Notification.NOTIFICATION;

public class EnrichedNotificationMapper implements RowMapper<EnrichedNotification> {

    private final RowMapper<Notification> notificationRowMapper;
    private final RowMapper<Dispute> disputeRowMapper;

    public EnrichedNotificationMapper() {
        notificationRowMapper = new RecordRowMapper<>(NOTIFICATION, Notification.class);
        disputeRowMapper = new RecordRowMapper<>(DISPUTE, Dispute.class);
    }

    @Override
    public EnrichedNotification mapRow(ResultSet resultSet, int i) throws SQLException {
        return EnrichedNotification.builder()
                .notification(notificationRowMapper.mapRow(resultSet, i))
                .dispute(disputeRowMapper.mapRow(resultSet, i))
                .build();
    }
}
