package dev.vality.disputes.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.tables.pojos.Notification;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.mapper.RecordRowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

import static dev.vality.disputes.domain.tables.Notification.NOTIFICATION;
import static dev.vality.disputes.exception.NotFoundException.Type;

@Component
@Slf4j
public class NotificationDao extends AbstractGenericDao {

    private final RowMapper<Notification> notificationRowMapper;

    @Autowired
    public NotificationDao(DataSource dataSource) {
        super(dataSource);
        notificationRowMapper = new RecordRowMapper<>(NOTIFICATION, Notification.class);
    }

    public void save(Notification notification) {
        var record = getDslContext().newRecord(NOTIFICATION, notification);
        var query = getDslContext().insertInto(NOTIFICATION)
                .set(record);
        executeOne(query);
    }

    public Notification get(UUID disputeId) {
        var query = getDslContext().selectFrom(NOTIFICATION)
                .where(NOTIFICATION.DISPUTE_ID.eq(disputeId));
        return Optional.ofNullable(fetchOne(query, notificationRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Notification not found, disputeId='%s'", disputeId), Type.NOTIFICATION));
    }
}
