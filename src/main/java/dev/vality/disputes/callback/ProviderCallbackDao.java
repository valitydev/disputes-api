package dev.vality.disputes.callback;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.mapper.RecordRowMapper;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.vality.disputes.domain.tables.Dispute.DISPUTE;
import static dev.vality.disputes.domain.tables.ProviderCallback.PROVIDER_CALLBACK;

@Component
@SuppressWarnings({"ParameterName", "LineLength"})
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
                .returning(DISPUTE.ID);
        var keyHolder = new GeneratedKeyHolder();
        execute(query, keyHolder);
        return Optional.ofNullable(keyHolder.getKeyAs(UUID.class)).orElseThrow();
    }

    @Nullable
    public ProviderCallback getProviderCallbackForUpdateSkipLocked(UUID id) {
        var query = getDslContext().selectFrom(PROVIDER_CALLBACK)
                .where(PROVIDER_CALLBACK.ID.eq(id))
                .forUpdate()
                .skipLocked();
        return fetchOne(query, providerCallbackRowMapper);
    }

    public List<ProviderCallback> getProviderCallbackForHgCall(int limit) {
        var query = getDslContext().selectFrom(PROVIDER_CALLBACK)
                .where(PROVIDER_CALLBACK.STATUS.eq(ProviderPaymentsStatus.create_adjustment)
                        .and(PROVIDER_CALLBACK.SKIP_CALL_HG_FOR_CREATE_ADJUSTMENT.eq(false)))
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
