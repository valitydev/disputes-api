package dev.vality.disputes.schedule.core;

import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.polling.PollingInfoService;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.schedule.catcher.WRuntimeExceptionCatcher;
import dev.vality.disputes.schedule.client.RemoteClient;
import dev.vality.disputes.schedule.result.DisputeStatusResultHandler;
import dev.vality.disputes.schedule.service.ProviderDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"MemberName", "ParameterName", "LineLength", "MissingSwitchDefault"})
public class PendingDisputesService {

    private final RemoteClient remoteClient;
    private final DisputeDao disputeDao;
    private final ProviderDisputeDao providerDisputeDao;
    private final PollingInfoService pollingInfoService;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;
    private final ProviderDataService providerDataService;
    private final DisputeStatusResultHandler disputeStatusResultHandler;
    private final WRuntimeExceptionCatcher wRuntimeExceptionCatcher;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getPendingDisputesForUpdateSkipLocked(int batchSize) {
        var locked = disputeDao.getDisputesForUpdateSkipLocked(batchSize, DisputeStatus.pending);
        if (!locked.isEmpty()) {
            log.debug("PendingDisputesForUpdateSkipLocked has been found, size={}", locked.size());
        }
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
        var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
        var providerDispute = providerDisputeDao.get(dispute.getId());
        if (providerDispute == null) {
            var nextCheckAfter = exponentialBackOffPollingService.prepareNextPollingInterval(dispute, providerData.getOptions());
            // вернуть в CreatedDisputeService и попробовать создать диспут в провайдере заново
            log.error("Trying to set created Dispute status, because createDispute() was not success {}", dispute.getId());
            disputeDao.update(dispute.getId(), DisputeStatus.created, nextCheckAfter);
            log.debug("Dispute status has been set to created {}", dispute.getId());
            return;
        }
        if (pollingInfoService.isDeadline(dispute)) {
            disputeStatusResultHandler.handlePoolingExpired(dispute);
            return;
        }
        log.debug("ProviderDispute has been found {}", dispute.getId());
        wRuntimeExceptionCatcher.catchUnexpectedResultMapping(
                () -> {
                    var result = remoteClient.checkDisputeStatus(dispute, providerDispute, providerData);
                    finishTask(dispute, result, providerData.getOptions());
                },
                e -> disputeStatusResultHandler.handleUnexpectedResultMapping(dispute, e));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void finishTask(Dispute dispute, DisputeStatusResult result, Map<String, String> options) {
        switch (result.getSetField()) {
            case STATUS_SUCCESS -> disputeStatusResultHandler.handleStatusSuccess(dispute, result);
            case STATUS_FAIL -> disputeStatusResultHandler.handleStatusFail(dispute, result);
            case STATUS_PENDING -> disputeStatusResultHandler.handleStatusPending(dispute, result, options);
        }
    }
}
