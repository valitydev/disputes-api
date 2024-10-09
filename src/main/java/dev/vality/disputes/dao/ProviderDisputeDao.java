package dev.vality.disputes.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.mapper.RecordRowMapper;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

import java.util.UUID;

import static dev.vality.disputes.domain.tables.ProviderDispute.PROVIDER_DISPUTE;

@Component
public class ProviderDisputeDao extends AbstractGenericDao {

    private final RowMapper<ProviderDispute> providerDisputeRowMapper;

    @Autowired
    public ProviderDisputeDao(DataSource dataSource) {
        super(dataSource);
        providerDisputeRowMapper = new RecordRowMapper<>(PROVIDER_DISPUTE, ProviderDispute.class);
    }

    public UUID save(ProviderDispute providerDispute) {
        var record = getDslContext().newRecord(PROVIDER_DISPUTE, providerDispute);
        var query = getDslContext().insertInto(PROVIDER_DISPUTE)
                .set(record);
        executeOne(query);
        return providerDispute.getDisputeId();
    }

    @Nullable
    public ProviderDispute get(UUID disputeId) {
        var query = getDslContext().selectFrom(PROVIDER_DISPUTE)
                .where(PROVIDER_DISPUTE.DISPUTE_ID.eq(disputeId));
        return fetchOne(query, providerDisputeRowMapper);
    }
}
