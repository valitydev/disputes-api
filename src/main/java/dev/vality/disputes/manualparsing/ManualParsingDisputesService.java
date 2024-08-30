package dev.vality.disputes.manualparsing;

import dev.vality.disputes.admin.ApproveParams;
import dev.vality.disputes.admin.BindParams;
import dev.vality.disputes.admin.CancelParams;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static dev.vality.disputes.api.service.ApiDisputesService.DISPUTE_PENDING;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class ManualParsingDisputesService {

    private final DisputeDao disputeDao;
    private final ProviderDisputeDao providerDisputeDao;

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    public void cancelPendingDispute(CancelParams cancelParams) {
        var disputeId = cancelParams.getDisputeId();
        var cancelReason = cancelParams.getCancelReason().orElse(null);
        log.debug("Trying to getForUpdateSkipLocked {}", disputeId);
        var dispute = disputeDao.getForUpdateSkipLocked(Long.parseLong(disputeId));
        log.debug("GetForUpdateSkipLocked has been found {}", dispute);
        if (DISPUTE_PENDING.contains(dispute.getStatus())) {
            // используется не failed, а cancelled чтоб можно было понять, что зафейлен по внешнему вызову
            log.warn("Trying to set cancelled Dispute status {}, {}", dispute, cancelReason);
            disputeDao.update(dispute.getId(), DisputeStatus.cancelled, cancelReason);
            log.debug("Dispute status has been set to cancelled {}", dispute);
        } else {
            log.info("Request was skipped by inappropriate status {}", dispute);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    public void approvePendingDispute(ApproveParams approveParam) {
        var disputeId = approveParam.getDisputeId();
        log.debug("Trying to getForUpdateSkipLocked {}", disputeId);
        var dispute = disputeDao.getForUpdateSkipLocked(Long.parseLong(disputeId));
        log.debug("GetForUpdateSkipLocked has been found {}", dispute);
        var skipCallHg = approveParam.isSkipCallHgForCreateAdjustment();
        var targetStatus = skipCallHg ? DisputeStatus.succeeded : DisputeStatus.create_adjustment;
        if (dispute.getStatus() == DisputeStatus.create_adjustment) {
            log.info("Trying to set {} Dispute status {}", targetStatus, dispute);
            // changedAmount не обновляем, тк уже заапрувлено на этапе чек статуса
            disputeDao.update(dispute.getId(), targetStatus, null, skipCallHg);
            log.debug("Dispute status has been set to {} {}", targetStatus, dispute);
            return;
        }
        if (dispute.getStatus() == DisputeStatus.pending
                || dispute.getStatus() == DisputeStatus.manual_pending) {
            log.info("Trying to set {} Dispute status {}", targetStatus, dispute);
            disputeDao.update(dispute.getId(), targetStatus, approveParam.getChangedAmount().orElse(null), skipCallHg);
            log.debug("Dispute status has been set to {} {}", targetStatus, dispute);
            return;
        }
        log.info("Request was skipped by inappropriate status {}", dispute);
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    public void bindCreatedDispute(BindParams bindParam) {
        var disputeId = bindParam.getDisputeId();
        var providerDisputeId = bindParam.getProviderDisputeId();
        log.debug("Trying to getForUpdateSkipLocked {}", disputeId);
        var dispute = disputeDao.getForUpdateSkipLocked(Long.parseLong(disputeId));
        log.debug("GetForUpdateSkipLocked has been found {}", dispute);
        if (dispute.getStatus() == DisputeStatus.manual_created) {
            // обрабатываем здесь только вручную созданные диспуты, у остальных предполагается,
            // что providerDisputeId будет сохранен после создания диспута по API провайдера
            log.info("Trying to set manual_parsing_binded_pending Dispute status {}", dispute);
            providerDisputeDao.save(new ProviderDispute(providerDisputeId, dispute.getId()));
            disputeDao.update(dispute.getId(), DisputeStatus.manual_pending);
            log.debug("Dispute status has been set to manual_parsing_binded_pending {}", dispute);
        } else {
            log.info("Request was skipped by inappropriate status {}", dispute);
        }
    }
}
