package dev.vality.disputes.schedule.result;

import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class ErrorResultHandler {

    private final DisputeDao disputeDao;

    @Transactional
    public void updateFailed(Dispute dispute, String errorReason) {
        log.error("Trying to set failed Dispute status with {} error reason {}", errorReason, dispute.getId());
        disputeDao.update(dispute.getId(), DisputeStatus.failed, errorReason);
        log.debug("Dispute status has been set to failed {}", dispute.getId());
    }
}
