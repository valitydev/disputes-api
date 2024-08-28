package dev.vality.disputes.service;

import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class DisputesService {

    private final DisputeDao disputeDao;

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    public void cancelPendingDispute(String disputeId, String cancelReason) {
        log.debug("Trying to getForUpdateSkipLocked {}", disputeId);
        var dispute = disputeDao.getForUpdateSkipLocked(Long.parseLong(disputeId));
        log.debug("GetForUpdateSkipLocked has been found {}", dispute);
        if (dispute.getStatus() == DisputeStatus.pending
                || dispute.getStatus() == DisputeStatus.created) {
            log.error("Trying to set cancelled Dispute status with '{}' error reason {}", cancelReason, dispute);
            disputeDao.update(dispute.getId(), DisputeStatus.cancelled, cancelReason);
            log.debug("Dispute status has been set to cancelled {}", dispute);
        }
    }
}
