package dev.vality.disputes.schedule.service;

import dev.vality.disputes.DisputeStatusResult;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.polling.PollingInfoService;
import dev.vality.disputes.schedule.client.RemoteClient;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class PendingDisputesService {

    private final RemoteClient remoteClient;
    private final DisputeDao disputeDao;
    private final ProviderDisputeDao providerDisputeDao;
    private final PollingInfoService pollingInfoService;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getPendingDisputesForUpdateSkipLocked(int batchSize) {
        log.debug("Trying to getPendingDisputesForUpdateSkipLocked");
        var locked = disputeDao.getDisputesForUpdateSkipLocked(batchSize, DisputeStatus.pending);
        log.debug("PendingDisputesForUpdateSkipLocked has been found, size={}", locked.size());
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    public void callPendingDisputeRemotely(Dispute dispute) {
        log.debug("Trying to getDisputeForUpdateSkipLocked {}", dispute);
        var forUpdate = disputeDao.getDisputeForUpdateSkipLocked(dispute.getId());
        if (forUpdate == null || forUpdate.getStatus() != DisputeStatus.pending) {
            log.debug("Dispute locked or wrong status {}", forUpdate);
            return;
        }
        log.debug("GetDisputeForUpdateSkipLocked has been found {}", dispute);
        log.debug("Trying to get ProviderDispute {}", dispute.getId());
        var providerDispute = providerDisputeDao.get(dispute.getId());
        if (providerDispute == null) {
            var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute);
            // вернуть в CreatedDisputeService и попробовать создать диспут в провайдере заново
            log.error("Trying to set created Dispute status, because createDispute() was not success {}", dispute.getId());
            disputeDao.update(dispute.getId(), DisputeStatus.created, nextCheckAfter);
            log.debug("Dispute status has been set to created {}", dispute.getId());
            return;
        }
        if (pollingInfoService.isDeadline(dispute)) {
            log.error("Trying to set failed Dispute status with POOLING_EXPIRED error reason {}", dispute.getId());
            disputeDao.update(dispute.getId(), DisputeStatus.failed, ErrorReason.POOLING_EXPIRED);
            log.debug("Dispute status has been set to failed {}", dispute.getId());
            return;
        }
        log.debug("ProviderDispute has been found {}", dispute.getId());
        var result = remoteClient.checkDisputeStatus(dispute, providerDispute);
        finishTask(dispute, result);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void finishTask(Dispute dispute, DisputeStatusResult result) {
        switch (result.getSetField()) {
            case STATUS_SUCCESS -> {
                var changedAmount = result.getStatusSuccess().getChangedAmount().orElse(null);
                log.info("Trying to set create_adjustment Dispute status {}, {}", dispute, result);
                disputeDao.update(dispute.getId(), DisputeStatus.create_adjustment, changedAmount);
                log.debug("Dispute status has been set to create_adjustment {}", dispute.getId());
            }
            case STATUS_FAIL -> {
                var errorMessage = TErrorUtil.toStringVal(result.getStatusFail().getFailure());
                log.warn("Trying to set failed Dispute status {}, {}", dispute.getId(), errorMessage);
                disputeDao.update(dispute.getId(), DisputeStatus.failed, errorMessage);
                log.debug("Dispute status has been set to failed {}", dispute.getId());
            }
            case STATUS_PENDING -> {
                // дергаем update() чтоб обновить время вызова next_check_after,
                // чтобы шедулатор далее доставал пачку самых древних диспутов и смещал
                // и этим вызовом мы финализируем состояние диспута, что он был обновлен недавно
                var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute);
                log.info("Trying to set pending Dispute status {}, {}", dispute, result);
                disputeDao.update(dispute.getId(), DisputeStatus.pending, nextCheckAfter);
                log.debug("Dispute status has been set to pending {}", dispute.getId());
            }
        }
    }
}
