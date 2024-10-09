package dev.vality.disputes.manualparsing;

import dev.vality.disputes.admin.*;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.FileMetaDao;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static dev.vality.disputes.api.service.ApiDisputesService.DISPUTE_PENDING;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class ManualParsingDisputesService {

    private final DisputeDao disputeDao;
    private final ProviderDisputeDao providerDisputeDao;
    private final FileMetaDao fileMetaDao;
    private final FileStorageService fileStorageService;
    private final CloseableHttpClient httpClient;

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    public void cancelPendingDispute(CancelParams cancelParams) {
        var disputeId = cancelParams.getDisputeId();
        log.debug("Trying to getForUpdateSkipLocked {}", disputeId);
        var dispute = disputeDao.getDisputeForUpdateSkipLocked(UUID.fromString(disputeId));
        if (dispute == null) {
            return;
        }
        var cancelReason = cancelParams.getCancelReason().orElse(null);
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
        var dispute = disputeDao.getDisputeForUpdateSkipLocked(UUID.fromString(disputeId));
        if (dispute == null) {
            return;
        }
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
        log.debug("Trying to getForUpdateSkipLocked {}", disputeId);
        var dispute = disputeDao.getDisputeForUpdateSkipLocked(UUID.fromString(disputeId));
        if (dispute == null) {
            return;
        }
        var providerDisputeId = bindParam.getProviderDisputeId();
        log.debug("GetForUpdateSkipLocked has been found {}", dispute);
        if (dispute.getStatus() == DisputeStatus.manual_created) {
            // обрабатываем здесь только вручную созданные диспуты, у остальных предполагается,
            // что providerDisputeId будет сохранен после создания диспута по API провайдера
            log.info("Trying to set manual_pending Dispute status {}", dispute);
            providerDisputeDao.save(new ProviderDispute(providerDisputeId, dispute.getId()));
            disputeDao.update(dispute.getId(), DisputeStatus.manual_pending);
            log.debug("Dispute status has been set to manual_pending {}", dispute);
        } else if (dispute.getStatus() == DisputeStatus.already_exist_created) {
            log.info("Trying to set pending Dispute status {}", dispute);
            providerDisputeDao.save(new ProviderDispute(providerDisputeId, dispute.getId()));
            disputeDao.update(dispute.getId(), DisputeStatus.pending);
            log.debug("Dispute status has been set to pending {}", dispute);
        } else {
            log.info("Request was skipped by inappropriate status {}", dispute);
        }
    }

    @SneakyThrows
    public Dispute getDispute(DisputeParams disputeParams, boolean withAttachments) {
        var disputeId = disputeParams.getDisputeId();
        var disputeOptional = disputeDao.get(UUID.fromString(disputeId));
        if (disputeOptional.isEmpty()) {
            log.debug("Trying to get Dispute but null {}", disputeId);
            return null;
        }
        var dispute = disputeOptional.get();
        var disputeResult = new Dispute();
        disputeResult.setDisputeId(disputeId);
        disputeResult.setProviderDisputeId(Optional.ofNullable(providerDisputeDao.get(dispute.getId()))
                .map(ProviderDispute::getProviderDisputeId)
                .orElse(null));
        disputeResult.setInvoiceId(dispute.getInvoiceId());
        disputeResult.setPaymentId(dispute.getPaymentId());
        disputeResult.setProviderTrxId(dispute.getProviderTrxId());
        disputeResult.setStatus(dispute.getStatus().name());
        disputeResult.setErrorMessage(dispute.getErrorMessage());
        disputeResult.setAmount(String.valueOf(dispute.getAmount()));
        disputeResult.setChangedAmount(Optional.ofNullable(dispute.getChangedAmount())
                .map(String::valueOf)
                .orElse(null));
        disputeResult.setSkipCallHgForCreateAdjustment(dispute.getSkipCallHgForCreateAdjustment());
        log.debug("Dispute getDispute {}", disputeResult);
        var disputeFiles = fileMetaDao.getDisputeFiles(dispute.getId());
        if (disputeFiles == null || !withAttachments) {
            return disputeResult;
        }
        disputeResult.setAttachments(new ArrayList<>());
        for (var disputeFile : disputeFiles) {
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
        return disputeResult;
    }
}
