package dev.vality.disputes.admin.management;

import dev.vality.adapter.flow.lib.model.PollingInfo;
import dev.vality.disputes.admin.*;
import dev.vality.disputes.admin.converter.DisputeThriftConverter;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.polling.PollingInfoService;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.result.DisputeStatusResultHandler;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.service.external.InvoicingService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static dev.vality.disputes.service.DisputesService.DISPUTE_PENDING_STATUSES;
import static dev.vality.disputes.util.DisputeStatusResultUtil.getDisputeStatusResult;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminManagementDisputesService {

    private final DisputeThriftConverter disputeThriftConverter;
    private final ProviderDisputeDao providerDisputeDao;
    private final DisputesService disputesService;
    private final ProviderDataService providerDataService;
    private final PollingInfoService pollingInfoService;
    private final InvoicingService invoicingService;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;
    private final DisputeStatusResultHandler disputeStatusResultHandler;

    @Transactional
    public void cancelPendingDispute(CancelParams params) {
        var dispute = disputesService.getSkipLockedByInvoiceId(params.getInvoiceId(), params.getPaymentId());
        if (DISPUTE_PENDING_STATUSES.contains(dispute.getStatus())) {
            // используется не failed, а cancelled чтоб можно было понять, что зафейлен по внешнему вызову
            disputesService.finishCancelled(
                    dispute,
                    params.getMapping().orElse(null),
                    params.getAdminMessage().orElse(null));
        }
    }

    @Transactional
    public void approvePendingDispute(ApproveParams params) {
        var dispute = disputesService.getSkipLockedByInvoiceId(params.getInvoiceId(), params.getPaymentId());
        var changedAmount = params.getChangedAmount()
                .filter(s -> dispute.getStatus() == DisputeStatus.pending
                        || dispute.getStatus() == DisputeStatus.manual_pending
                        || dispute.getStatus() == DisputeStatus.pooling_expired)
                .orElse(null);
        if ((dispute.getStatus() == DisputeStatus.pending
                || dispute.getStatus() == DisputeStatus.manual_pending
                || dispute.getStatus() == DisputeStatus.pooling_expired)
                && !params.isSkipCallHgForCreateAdjustment()) {
            var invoicePayment = invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            // если ProviderPaymentsUnexpectedPaymentStatus то нехрен апрувить не успешный платеж
            disputeStatusResultHandler.handleCreateAdjustmentResult(
                    dispute,
                    getDisputeStatusResult(changedAmount),
                    providerData,
                    invoicePayment.getLastTransactionInfo(),
                    params.getAdminMessage().orElse(null));
        } else if (dispute.getStatus() == DisputeStatus.pending
                || dispute.getStatus() == DisputeStatus.manual_pending
                || dispute.getStatus() == DisputeStatus.pooling_expired
                || dispute.getStatus() == DisputeStatus.create_adjustment) {
            disputesService.finishSucceeded(dispute, changedAmount, params.getAdminMessage().orElse(null));
        }
    }

    @Transactional
    public void bindCreatedDispute(BindParams params) {
        var disputeId = params.getDisputeId();
        var dispute = disputesService.getSkipLocked(disputeId);
        var providerDisputeId = params.getProviderDisputeId();
        if (dispute.getStatus() == DisputeStatus.already_exist_created) {
            providerDisputeDao.save(providerDisputeId, dispute);
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            disputesService.setNextStepToPending(dispute, providerData);
        }
    }

    @SneakyThrows
    public Dispute getDispute(DisputeParams params, boolean withAttachments) {
        var dispute = disputesService.getByInvoiceId(params.getInvoiceId(), params.getPaymentId());
        return disputeThriftConverter.convert(dispute, withAttachments);
    }

    @Transactional
    public void setPendingForPoolingExpiredDispute(SetPendingForPoolingExpiredParams params) {
        var dispute = disputesService.getSkipLockedByInvoiceId(params.getInvoiceId(), params.getPaymentId());
        if (dispute.getStatus() == DisputeStatus.pooling_expired) {
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            var pollingInfo = pollingInfoService.initPollingInfo(providerData.getOptions());
            dispute.setNextCheckAfter(getNextCheckAfter(providerData, pollingInfo));
            dispute.setPollingBefore(getLocalDateTime(pollingInfo.getMaxDateTimePolling()));
            disputesService.setNextStepToPending(dispute, providerData);
        }
    }

    private LocalDateTime getLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private LocalDateTime getNextCheckAfter(ProviderData providerData, PollingInfo pollingInfo) {
        return exponentialBackOffPollingService.prepareNextPollingInterval(pollingInfo, providerData.getOptions());
    }
}
