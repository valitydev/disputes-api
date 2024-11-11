package dev.vality.disputes.provider.payments.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.tables.pojos.RetryProviderPaymentCheckStatus;
import dev.vality.mapper.RecordRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

import static dev.vality.disputes.domain.tables.RetryProviderPaymentCheckStatus.RETRY_PROVIDER_PAYMENT_CHECK_STATUS;

@Component
@SuppressWarnings({"LineLength"})
public class RetryProviderPaymentCheckStatusDao extends AbstractGenericDao {

    private final RowMapper<RetryProviderPaymentCheckStatus> retryProviderPaymentStatusFinalizationRowMapper;

    @Autowired
    public RetryProviderPaymentCheckStatusDao(DataSource dataSource) {
        super(dataSource);
        retryProviderPaymentStatusFinalizationRowMapper = new RecordRowMapper<>(RETRY_PROVIDER_PAYMENT_CHECK_STATUS, RetryProviderPaymentCheckStatus.class);
    }

    public UUID save(RetryProviderPaymentCheckStatus retryProviderPaymentCheckStatus) {
        var record = getDslContext().newRecord(RETRY_PROVIDER_PAYMENT_CHECK_STATUS, retryProviderPaymentCheckStatus);
        var query = getDslContext().insertInto(RETRY_PROVIDER_PAYMENT_CHECK_STATUS)
                .set(record)
                .returning(RETRY_PROVIDER_PAYMENT_CHECK_STATUS.ID);
        var keyHolder = new GeneratedKeyHolder();
        execute(query, keyHolder);
        return Optional.ofNullable(keyHolder.getKeyAs(UUID.class)).orElseThrow();
    }
}
