package dev.vality.disputes.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.mapper.RecordRowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

import static dev.vality.disputes.domain.tables.ProviderDispute.PROVIDER_DISPUTE;
import static dev.vality.disputes.exception.NotFoundException.Type;

@Component
@Slf4j
public class ProviderDisputeDao extends AbstractGenericDao {

    private final RowMapper<ProviderDispute> providerDisputeRowMapper;

    public ProviderDisputeDao(DataSource dataSource) {
        super(dataSource);
        providerDisputeRowMapper = new RecordRowMapper<>(PROVIDER_DISPUTE, ProviderDispute.class);
    }

    public void save(String providerDisputeId, Dispute dispute) {
        var id = save(new ProviderDispute(providerDisputeId, dispute.getId()));
        log.debug("ProviderDispute has been saved {}", id);
    }

    public UUID save(ProviderDispute providerDispute) {
        var record = getDslContext().newRecord(PROVIDER_DISPUTE, providerDispute);
        var query = getDslContext().insertInto(PROVIDER_DISPUTE)
                .set(record);
        executeOne(query);
        return providerDispute.getDisputeId();
    }

    public ProviderDispute get(UUID disputeId) {
        var query = getDslContext().selectFrom(PROVIDER_DISPUTE)
                .where(PROVIDER_DISPUTE.DISPUTE_ID.eq(disputeId));
        return Optional.ofNullable(fetchOne(query, providerDisputeRowMapper))
                .orElseThrow(() -> new NotFoundException(
                        String.format("ProviderDispute not found, disputeId='%s'", disputeId), Type.PROVIDERDISPUTE));
    }
}
