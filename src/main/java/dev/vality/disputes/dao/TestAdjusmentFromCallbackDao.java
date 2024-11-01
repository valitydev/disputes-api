package dev.vality.disputes.dao;

import dev.vality.dao.impl.AbstractGenericDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.TestAdjustmentFromCallback;
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
import static dev.vality.disputes.domain.tables.TestAdjustmentFromCallback.TEST_ADJUSTMENT_FROM_CALLBACK;

@Component
@SuppressWarnings({"ParameterName", "LineLength"})
public class TestAdjusmentFromCallbackDao extends AbstractGenericDao {

    private final RowMapper<TestAdjustmentFromCallback> testAdjustmentFromCallbackRowMapper;

    @Autowired
    public TestAdjusmentFromCallbackDao(DataSource dataSource) {
        super(dataSource);
        testAdjustmentFromCallbackRowMapper = new RecordRowMapper<>(TEST_ADJUSTMENT_FROM_CALLBACK, TestAdjustmentFromCallback.class);
    }

    public UUID save(TestAdjustmentFromCallback testAdjustmentFromCallback) {
        var record = getDslContext().newRecord(TEST_ADJUSTMENT_FROM_CALLBACK, testAdjustmentFromCallback);
        var query = getDslContext().insertInto(TEST_ADJUSTMENT_FROM_CALLBACK)
                .set(record)
                .returning(DISPUTE.ID);
        var keyHolder = new GeneratedKeyHolder();
        execute(query, keyHolder);
        return Optional.ofNullable(keyHolder.getKeyAs(UUID.class)).orElseThrow();
    }

    @Nullable
    public TestAdjustmentFromCallback getTestAdjustmentFromCallbackForUpdateSkipLocked(UUID id) {
        var query = getDslContext().selectFrom(TEST_ADJUSTMENT_FROM_CALLBACK)
                .where(TEST_ADJUSTMENT_FROM_CALLBACK.ID.eq(id))
                .forUpdate()
                .skipLocked();
        return fetchOne(query, testAdjustmentFromCallbackRowMapper);
    }

    public List<TestAdjustmentFromCallback> getTestAdjustmentFromCallbackForHgCall(int limit) {
        var query = getDslContext().selectFrom(TEST_ADJUSTMENT_FROM_CALLBACK)
                .where(TEST_ADJUSTMENT_FROM_CALLBACK.STATUS.eq(DisputeStatus.create_adjustment)
                        .and(TEST_ADJUSTMENT_FROM_CALLBACK.SKIP_CALL_HG_FOR_CREATE_ADJUSTMENT.eq(false)))
                .limit(limit)
                .forUpdate()
                .skipLocked();
        return Optional.ofNullable(fetch(query, testAdjustmentFromCallbackRowMapper))
                .orElse(List.of());
    }
}
