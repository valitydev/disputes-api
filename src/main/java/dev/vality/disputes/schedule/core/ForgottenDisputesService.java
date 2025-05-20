package dev.vality.disputes.schedule.core;

import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.CapturedPaymentException;
import dev.vality.disputes.exception.DisputeStatusWasUpdatedByAnotherThreadException;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.disputes.util.PaymentStatusValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static dev.vality.disputes.util.PaymentAmountUtil.getChangedAmount;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForgottenDisputesService {

    private final DisputesService disputesService;
    private final InvoicingService invoicingService;
    private final ProviderDataService providerDataService;

    @Transactional
    public List<Dispute> getForgottenSkipLocked(int batchSize) {
        return disputesService.getForgottenSkipLocked(batchSize);
    }

    @Transactional
    public void process(Dispute dispute) {
        try {
            // validate
            disputesService.checkPendingStatuses(dispute);
            // validate
            var invoicePayment = invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
            // validate
            PaymentStatusValidator.checkStatus(invoicePayment);
            var providerData = providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
            disputesService.updateNextPollingInterval(dispute, providerData);
        } catch (NotFoundException ex) {
            log.error("NotFound when handle ForgottenDisputesService.process, type={}", ex.getType(), ex);
            switch (ex.getType()) {
                case INVOICE -> disputesService.finishFailed(dispute, ErrorMessage.INVOICE_NOT_FOUND);
                case PAYMENT -> disputesService.finishFailed(dispute, ErrorMessage.PAYMENT_NOT_FOUND);
                case DISPUTE -> log.debug("Dispute locked {}", dispute);
                default -> throw ex;
            }
        } catch (CapturedPaymentException ex) {
            log.info("CapturedPaymentException when handle ForgottenDisputesService.process", ex);
            disputesService.finishSucceeded(dispute, getChangedAmount(ex.getInvoicePayment().getPayment()));
        } catch (InvoicingPaymentStatusRestrictionsException ex) {
            log.error("InvoicingPaymentRestrictionStatus when handle ForgottenDisputesService.process", ex);
            disputesService.finishFailed(dispute,
                    PaymentStatusValidator.getInvoicingPaymentStatusRestrictionsErrorReason(ex));
        } catch (DisputeStatusWasUpdatedByAnotherThreadException ex) {
            log.debug("DisputeStatusWasUpdatedByAnotherThread when handle ForgottenDisputesService.process", ex);
        }
    }
}
