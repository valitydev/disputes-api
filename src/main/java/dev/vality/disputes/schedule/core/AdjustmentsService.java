package dev.vality.disputes.schedule.core;

import dev.vality.damsel.domain.InvoicePaymentAdjustment;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.schedule.converter.InvoicePaymentCapturedAdjustmentParamsConverter;
import dev.vality.disputes.schedule.converter.InvoicePaymentCashFlowAdjustmentParamsConverter;
import dev.vality.disputes.schedule.converter.InvoicePaymentFailedAdjustmentParamsConverter;
import dev.vality.disputes.schedule.result.ErrorResultHandler;
import dev.vality.disputes.schedule.service.AdjustmentExtractor;
import dev.vality.disputes.service.external.InvoicingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class AdjustmentsService {

    private final DisputeDao disputeDao;
    private final InvoicingService invoicingService;
    private final InvoicePaymentCapturedAdjustmentParamsConverter invoicePaymentCapturedAdjustmentParamsConverter;
    private final InvoicePaymentCashFlowAdjustmentParamsConverter invoicePaymentCashFlowAdjustmentParamsConverter;
    private final InvoicePaymentFailedAdjustmentParamsConverter invoicePaymentFailedAdjustmentParamsConverter;
    private final AdjustmentExtractor adjustmentExtractor;
    private final ErrorResultHandler errorResultHandler;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getDisputesForHgCall(int batchSize) {
        var locked = disputeDao.getDisputesForHgCall(batchSize);
        if (!locked.isEmpty()) {
            log.debug("getDisputesForHgCall has been found, size={}", locked.size());
        }
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    public void callHgForCreateAdjustment(Dispute dispute) {
        log.debug("Trying to getDisputeForUpdateSkipLocked {}", dispute);
        var forUpdate = disputeDao.getDisputeForUpdateSkipLocked(dispute.getId());
        if (forUpdate == null || forUpdate.getStatus() != DisputeStatus.create_adjustment) {
            log.debug("Dispute locked or wrong status {}", forUpdate);
            return;
        }
        log.debug("GetDisputeForUpdateSkipLocked has been found {}", dispute);
        var invoicePayment = getInvoicePayment(dispute);
        if (invoicePayment == null || !invoicePayment.isSetRoute()) {
            errorResultHandler.updateFailed(dispute, ErrorReason.PAYMENT_NOT_FOUND);
            return;
        }
        if (!adjustmentExtractor.isCashFlowAdjustmentByDisputeExist(invoicePayment, dispute)
                && !Objects.equals(dispute.getAmount(), dispute.getChangedAmount())) {
            var params = invoicePaymentCashFlowAdjustmentParamsConverter.convert(dispute);
            if (!createAdjustment(dispute, params)) {
                return;
            }
        } else {
            log.info("Creating CashFlowAdjustment was skipped {}", dispute);
        }
        if (!adjustmentExtractor.isCapturedAdjustmentByDisputeExist(invoicePayment, dispute)) {
            if (invoicePayment.getPayment().getStatus().isSetCaptured()) {
                var params = invoicePaymentFailedAdjustmentParamsConverter.convert(dispute);
                if (!createAdjustment(dispute, params)) {
                    return;
                }
            }
            var params = invoicePaymentCapturedAdjustmentParamsConverter.convert(dispute);
            if (!createAdjustment(dispute, params)) {
                return;
            }
        } else {
            log.info("Creating CapturedAdjustment was skipped {}", dispute);
        }
        log.info("Trying to set succeeded Dispute status {}", dispute);
        disputeDao.update(dispute.getId(), DisputeStatus.succeeded);
        log.debug("Dispute status has been set to succeeded {}", dispute.getId());
    }


    @Transactional(propagation = Propagation.REQUIRED)
    boolean createAdjustment(Dispute dispute, InvoicePaymentAdjustmentParams params) {
        var paymentAdjustment = createPaymentAdjustment(dispute, params);
        if (paymentAdjustment == null) {
            errorResultHandler.updateFailed(dispute, ErrorReason.INVOICE_NOT_FOUND);
            return false;
        }
        return true;
    }

    private InvoicePaymentAdjustment createPaymentAdjustment(Dispute dispute, InvoicePaymentAdjustmentParams params) {
        return invoicingService.createPaymentAdjustment(dispute.getInvoiceId(), dispute.getPaymentId(), params);
    }

    private InvoicePayment getInvoicePayment(Dispute dispute) {
        return invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
    }
}
