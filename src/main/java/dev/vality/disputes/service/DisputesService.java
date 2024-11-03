package dev.vality.disputes.service;

import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.vality.disputes.api.service.ApiDisputesService.DISPUTE_PENDING;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class DisputesService {
    private final DisputeDao disputeDao;

    @Transactional
    public void finishSuccess(String invoiceId, String paymentId) {
        var disputes = disputeDao.get(invoiceId, paymentId).stream()
                .filter(dispute -> DISPUTE_PENDING.contains(dispute.getStatus()))
                .toList();
        for (int i = 0; i < disputes.size(); i++) {
            var dispute = disputeDao.getDisputeForUpdateSkipLocked(disputes.get(i).getId());
            if (dispute == null || !DISPUTE_PENDING.contains(dispute.getStatus())) {
                log.debug("Dispute locked or wrong status {}", dispute);
                return;
            }
            if (i == disputes.size() - 1) {
                log.info("Trying to set succeeded Dispute status {}", dispute);
                disputeDao.update(dispute.getId(), DisputeStatus.succeeded);
                log.debug("Dispute status has been set to succeeded {}", dispute.getId());
            } else {
                log.info("Trying to set failed Dispute status {}", dispute);
                disputeDao.update(dispute.getId(), DisputeStatus.failed);
                log.debug("Dispute status has been set to failed {}", dispute.getId());
            }
        }
    }
}
