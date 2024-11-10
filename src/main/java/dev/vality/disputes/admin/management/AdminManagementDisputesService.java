package dev.vality.disputes.admin.management;

import dev.vality.disputes.admin.*;
import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.dao.FileMetaDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import dev.vality.disputes.exception.NotFoundException;
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
                        || dispute.getStatus() == DisputeStatus.manual_pending)
                .orElse(null);
        if (dispute.getStatus() == DisputeStatus.pending
                || dispute.getStatus() == DisputeStatus.manual_pending
                || dispute.getStatus() == DisputeStatus.create_adjustment) {
            disputesService.finishSucceeded(dispute, changedAmount);
        } else {
            log.debug("Request was skipped by inappropriate status {}", dispute);
        }
    }

    @Transactional
    public void bindCreatedDispute(BindParams bindParam) {
        var disputeId = bindParam.getDisputeId();
        var dispute = disputesService.getDisputeForUpdateSkipLocked(disputeId);
        var providerDisputeId = bindParam.getProviderDisputeId();
        if (dispute.getStatus() == DisputeStatus.manual_created) {
            // обрабатываем здесь только вручную созданные диспуты, у остальных предполагается,
            // что providerDisputeId будет сохранен после создания диспута по API провайдера
            providerDisputeDao.save(providerDisputeId, dispute);
            disputesService.setNextStepToManualPending(dispute, ErrorMessage.NEXT_STEP_AFTER_BIND_PARAMS);
        } else if (dispute.getStatus() == DisputeStatus.already_exist_created) {
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

    private Optional<ProviderDispute> getProviderDispute(dev.vality.disputes.domain.tables.pojos.Dispute dispute) {
        try {
            return Optional.of(providerDisputeDao.get(dispute.getId()));
        } catch (NotFoundException ex) {
            log.warn("NotFound when handle AdminManagementDisputesService.getDispute, type={}", ex.getType(), ex);
            return Optional.empty();
        }
    }
}
