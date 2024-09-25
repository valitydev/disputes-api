package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.InvoicePaymentAdjustment;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.damsel.payment_processing.InvoicePaymentAdjustmentParams;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.InvoicingPaymentStatusPendingException;
import dev.vality.disputes.schedule.converter.InvoicePaymentAdjustmentParamsConverter;
import dev.vality.disputes.service.external.InvoicingService;
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
public class CreateAdjustmentsService {

    private final DisputeDao disputeDao;
    private final InvoicingService invoicingService;
    private final InvoicePaymentAdjustmentParamsConverter invoicePaymentAdjustmentParamsConverter;
    private final AdjustmentExtractor adjustmentExtractor;

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getDisputesForHgCall(int batchSize) {
        log.debug("Trying to getDisputesForHgCall");
        var locked = disputeDao.getDisputesForHgCall(batchSize);
        log.debug("getDisputesForHgCall has been found, size={}", locked.size());
        return locked;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<Dispute> getReadyDisputesForCreateAdjustment() {
        log.debug("Trying to getReadyDisputesForCreateAdjustment");
        var locked = disputeDao.getReadyDisputesForCreateAdjustment();
        log.debug("getReadyDisputesForCreateAdjustment has been found, size={}", locked.size());
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
            log.error("Trying to set failed Dispute status with PAYMENT_NOT_FOUND error reason {}", dispute.getId());
            disputeDao.update(dispute.getId(), DisputeStatus.failed, ErrorReason.PAYMENT_NOT_FOUND);
            log.debug("Dispute status has been set to failed {}", dispute.getId());
            return;
        }
        var invoicePaymentAdjustment = adjustmentExtractor.searchAdjustmentByDispute(invoicePayment, dispute);
        if (invoicePaymentAdjustment.isPresent()) {
            var changedAmount = adjustmentExtractor.getChangedAmount(invoicePaymentAdjustment.get(), dispute.getChangedAmount());
            log.info("Trying to set succeeded Dispute status {}", dispute);
            disputeDao.update(dispute.getId(), DisputeStatus.succeeded, changedAmount);
            log.debug("Dispute status has been set to succeeded {}", dispute.getId());
            return;
        }
        try {
            var params = invoicePaymentAdjustmentParamsConverter.convert(dispute);
            var paymentAdjustment = createAdjustment(dispute, params);
            if (paymentAdjustment == null) {
                log.error("Trying to set failed Dispute status with INVOICE_NOT_FOUND error reason {}", dispute.getId());
                disputeDao.update(dispute.getId(), DisputeStatus.failed, ErrorReason.INVOICE_NOT_FOUND);
                log.debug("Dispute status has been set to failed {}", dispute.getId());
                return;
            }
        } catch (InvoicingPaymentStatusPendingException e) {
            // в теории 0%, что сюда попадает выполнение кода, но если попадет, то:
            // платеж с не финальным статусом будет заблочен для создания корректировок на стороне хелгейта
            // и тогда диспут будет пулиться, пока платеж не зафиналится,
            // и тк никакой записи в коде выше нет, то пуллинг не проблема
            // а запрос в checkDisputeStatus по идемпотентности просто вернет тот же success
            log.error("Error when hg.createPaymentAdjustment() got payments status pending {}", dispute.getId(), e);
            return;
        }
        log.info("Trying to set succeeded Dispute status {}", dispute);
        disputeDao.update(dispute.getId(), DisputeStatus.succeeded);
        log.debug("Dispute status has been set to succeeded {}", dispute.getId());
    }

    private InvoicePaymentAdjustment createAdjustment(Dispute dispute, InvoicePaymentAdjustmentParams params) {
        return invoicingService.createPaymentAdjustment(dispute.getInvoiceId(), dispute.getPaymentId(), params);
    }

    private InvoicePayment getInvoicePayment(Dispute dispute) {
        return invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
    }
}
