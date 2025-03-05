package dev.vality.disputes.provider.payments.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.mapper.RecordRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.vality.disputes.domain.tables.ProviderCallback.PROVIDER_CALLBACK;

@Component
@SuppressWarnings({"LineLength"})
public class ProviderCallbackDao extends AbstractGenericDao {

    private final RowMapper<ProviderCallback> providerCallbackRowMapper;

    @Autowired
    public ProviderCallbackDao(DataSource dataSource) {
        super(dataSource);
        providerCallbackRowMapper = new RecordRowMapper<>(PROVIDER_CALLBACK, ProviderCallback.class);
    }

    public UUID save(ProviderCallback providerCallback) {
        var record = getDslContext().newRecord(PROVIDER_CALLBACK, providerCallback);
        var query = getDslContext().insertInto(PROVIDER_CALLBACK)
                .set(record)
                .returning(PROVIDER_CALLBACK.ID);
        var keyHolder = new GeneratedKeyHolder();
        execute(query, keyHolder);
        return Optional.ofNullable(keyHolder.getKeyAs(UUID.class)).orElseThrow();
    }

    public ProviderCallback getProviderCallbackForUpdateSkipLocked(UUID id) {
        var query = getDslContext().selectFrom(PROVIDER_CALLBACK)
                .where(PROVIDER_CALLBACK.ID.eq(id))
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetchOne(query, providerCallbackRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("ProviderCallback not found, id='%s'", id), NotFoundException.Type.PROVIDERCALLBACK));
    }

    public ProviderCallback get(String invoiceId, String paymentId) {
        var query = getDslContext().selectFrom(PROVIDER_CALLBACK)
                .where(PROVIDER_CALLBACK.INVOICE_ID.concat(PROVIDER_CALLBACK.PAYMENT_ID).eq(invoiceId + paymentId));
        return Optional.ofNullable(fetchOne(query, providerCallbackRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("ProviderCallback not found, id='%s%s'", invoiceId, paymentId), NotFoundException.Type.PROVIDERCALLBACK));

    }

    public List<ProviderCallback> getProviderCallbacksForHgCall(int limit) {
        var query = getDslContext().selectFrom(PROVIDER_CALLBACK)
                .where(PROVIDER_CALLBACK.STATUS.eq(ProviderPaymentsStatus.create_adjustment))
                .limit(limit)
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetch(query, providerCallbackRowMapper))
                .orElse(List.of());
    }

    public void update(ProviderCallback providerCallback) {
        var record = getDslContext().newRecord(PROVIDER_CALLBACK, providerCallback);
        var query = getDslContext().update(PROVIDER_CALLBACK)
                .set(record)
                .where(PROVIDER_CALLBACK.ID.eq(providerCallback.getId()));
        execute(query);
    }
}
