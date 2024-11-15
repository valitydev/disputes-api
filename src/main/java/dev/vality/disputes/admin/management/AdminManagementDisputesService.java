package dev.vality.disputes.admin.management;

import dev.vality.adapter.flow.lib.model.PollingInfo;
import dev.vality.disputes.admin.*;
import dev.vality.disputes.dao.FileMetaDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.polling.PollingInfoService;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.provider.DisputeStatusSuccessResult;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.result.DisputeStatusResultHandler;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.service.external.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;

import static dev.vality.disputes.service.DisputesService.DISPUTE_PENDING_STATUSES;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class AdminManagementDisputesService {

    private final ProviderDisputeDao providerDisputeDao;
    private final FileMetaDao fileMetaDao;
    private final FileStorageService fileStorageService;
    private final DisputesService disputesService;
    private final ProviderDataService providerDataService;
    private final PollingInfoService pollingInfoService;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;
    private final DisputeStatusResultHandler disputeStatusResultHandler;
    private final CloseableHttpClient httpClient;

    @Transactional
    public void cancelPendingDispute(CancelParams cancelParams) {
        var disputeId = cancelParams.getDisputeId();
        var dispute = disputesService.getDisputeForUpdateSkipLocked(disputeId);
        if (DISPUTE_PENDING_STATUSES.contains(dispute.getStatus())) {
            // используется не failed, а cancelled чтоб можно было понять, что зафейлен по внешнему вызову
            disputesService.finishCancelled(dispute, cancelParams.getMapping().orElse(null), cancelParams.getCancelReason().orElse(null));
        } else {
            log.debug("Request was skipped by inappropriate status {}", dispute);
        }
    }

    @Transactional
    public void approvePendingDispute(ApproveParams approveParam) {
        var disputeId = approveParam.getDisputeId();
        var dispute = disputesService.getDisputeForUpdateSkipLocked(disputeId);
        var changedAmount = approveParam.getChangedAmount()
                .filter(s -> dispute.getStatus() == DisputeStatus.pending
                        || dispute.getStatus() == DisputeStatus.manual_pending
                        || dispute.getStatus() == DisputeStatus.pooling_expired);
        if ((dispute.getStatus() == DisputeStatus.pending
                || dispute.getStatus() == DisputeStatus.manual_pending
                || dispute.getStatus() == DisputeStatus.pooling_expired)
                && !approveParam.isSkipCallHgForCreateAdjustment()) {
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            // если ProviderPaymentsUnexpectedPaymentStatus то нехрен апрувить не успешный платеж
            handleSucceededResultWithCreateAdjustment(dispute, changedAmount.orElse(null), providerData);
        } else if (dispute.getStatus() == DisputeStatus.pending
                || dispute.getStatus() == DisputeStatus.manual_pending
                || dispute.getStatus() == DisputeStatus.pooling_expired
                || dispute.getStatus() == DisputeStatus.create_adjustment) {
            disputesService.finishSucceeded(dispute, changedAmount.orElse(null));
        } else {
            log.debug("Request was skipped by inappropriate status {}", dispute);
        }
    }

    @Transactional
    public void bindCreatedDispute(BindParams bindParam) {
        var disputeId = bindParam.getDisputeId();
        var dispute = disputesService.getDisputeForUpdateSkipLocked(disputeId);
        var providerDisputeId = bindParam.getProviderDisputeId();
        if (dispute.getStatus() == DisputeStatus.already_exist_created) {
            providerDisputeDao.save(providerDisputeId, dispute);
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            disputesService.setNextStepToPending(dispute, providerData);
        } else {
            log.debug("Request was skipped by inappropriate status {}", dispute);
        }
    }

    @SneakyThrows
    public Dispute getDispute(DisputeParams disputeParams, boolean withAttachments) {
        var disputeId = disputeParams.getDisputeId();
        var dispute = disputesService.get(disputeId);
        var disputeResult = new Dispute();
        disputeResult.setDisputeId(disputeId);
        disputeResult.setProviderDisputeId(getProviderDispute(dispute)
                .map(ProviderDispute::getProviderDisputeId)
                .orElse(null));
        disputeResult.setInvoiceId(dispute.getInvoiceId());
        disputeResult.setPaymentId(dispute.getPaymentId());
        disputeResult.setProviderTrxId(dispute.getProviderTrxId());
        disputeResult.setStatus(dispute.getStatus().name());
        disputeResult.setErrorMessage(dispute.getErrorMessage());
        disputeResult.setMapping(dispute.getMapping());
        disputeResult.setAmount(String.valueOf(dispute.getAmount()));
        disputeResult.setChangedAmount(Optional.ofNullable(dispute.getChangedAmount())
                .map(String::valueOf)
                .orElse(null));
        log.debug("Dispute getDispute {}", disputeResult);
        if (!withAttachments) {
            return disputeResult;
        }
        try {
            disputeResult.setAttachments(new ArrayList<>());
            for (var disputeFile : fileMetaDao.getDisputeFiles(dispute.getId())) {
                var downloadUrl = fileStorageService.generateDownloadUrl(disputeFile.getFileId());
                var data = httpClient.execute(
                        new HttpGet(downloadUrl),
                        new AbstractHttpClientResponseHandler<byte[]>() {
                            @Override
                            public byte[] handleEntity(HttpEntity entity) throws IOException {
                                return EntityUtils.toByteArray(entity);
                            }
                        });
                disputeResult.getAttachments().get().add(new Attachment().setData(data));
            }
        } catch (NotFoundException ex) {
            log.warn("NotFound when handle AdminManagementDisputesService.getDispute, type={}", ex.getType(), ex);
        }
        return disputeResult;
    }

    @Transactional
    public void setPendingForPoolingExpiredDispute(SetPendingForPoolingExpiredParams setPendingForPoolingExpiredParams) {
        var disputeId = setPendingForPoolingExpiredParams.getDisputeId();
        var dispute = disputesService.getDisputeForUpdateSkipLocked(disputeId);
        if (dispute.getStatus() == DisputeStatus.pooling_expired) {
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            var pollingInfo = pollingInfoService.initPollingInfo((dev.vality.disputes.domain.tables.pojos.Dispute) null, providerData.getOptions());
            dispute.setNextCheckAfter(getNextCheckAfter(providerData, pollingInfo));
            dispute.setPollingBefore(getLocalDateTime(pollingInfo.getMaxDateTimePolling()));
            disputesService.setNextStepToPending(dispute, providerData);
        } else {
            log.debug("Request was skipped by inappropriate status {}", dispute);
        }
    }

    private Optional<ProviderDispute> getProviderDispute(dev.vality.disputes.domain.tables.pojos.Dispute dispute) {
        try {
            return Optional.of(providerDisputeDao.get(dispute.getId()));
        } catch (NotFoundException ex) {
            log.warn("NotFound when handle AdminManagementDisputesService.getDispute, type={}", ex.getType(), ex);
            return Optional.empty();
        }
    }

    private void handleSucceededResultWithCreateAdjustment(dev.vality.disputes.domain.tables.pojos.Dispute dispute, Long changedAmount, ProviderData providerData) {
        disputeStatusResultHandler.handleSucceededResult(dispute, getDisputeStatusResult(changedAmount), providerData, false);
    }

    private DisputeStatusResult getDisputeStatusResult(Long changedAmount) {
        return Optional.ofNullable(changedAmount)
                .map(amount -> DisputeStatusResult.statusSuccess(new DisputeStatusSuccessResult().setChangedAmount(amount)))
                .orElse(DisputeStatusResult.statusSuccess(new DisputeStatusSuccessResult()));
    }

    private LocalDateTime getLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private LocalDateTime getNextCheckAfter(ProviderData providerData, PollingInfo pollingInfo) {
        return exponentialBackOffPollingService.prepareNextPollingInterval(pollingInfo, providerData.getOptions());
    }
}
