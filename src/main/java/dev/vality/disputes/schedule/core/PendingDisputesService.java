package dev.vality.disputes.schedule.core;

import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.*;
import dev.vality.disputes.polling.PollingInfoService;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.schedule.catcher.WoodyRuntimeExceptionCatcher;
import dev.vality.disputes.schedule.client.RemoteClient;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.result.DisputeStatusResultHandler;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.disputes.util.PaymentStatusValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

import static dev.vality.disputes.util.PaymentAmountUtil.getChangedAmount;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class PendingDisputesService {

    private final RemoteClient remoteClient;
    private final DisputesService disputesService;
    private final ProviderDisputeDao providerDisputeDao;
    private final InvoicingService invoicingService;
    private final PollingInfoService pollingInfoService;
    private final ProviderDataService providerDataService;
    private final DisputeStatusResultHandler disputeStatusResultHandler;
    private final WoodyRuntimeExceptionCatcher woodyRuntimeExceptionCatcher;

    @Transactional
    public List<Dispute> getPendingSkipLocked(int batchSize) {
        return disputesService.getPendingSkipLocked(batchSize);
    }

    @Transactional
    public void callPendingDisputeRemotely(Dispute dispute) {
        try {
            // validate
            disputesService.checkPendingStatus(dispute);
            // validate
            pollingInfoService.checkDeadline(dispute);
            // validate
            var invoicePayment = invoicingService.getInvoicePayment(dispute.getInvoiceId(), dispute.getPaymentId());
            // validate
            PaymentStatusValidator.checkStatus(invoicePayment);
            var providerData = getProviderData(dispute);
            var finishCheckDisputeStatusResult = (Consumer<DisputeStatusResult>) result -> {
                switch (result.getSetField()) {
                    case STATUS_SUCCESS -> disputeStatusResultHandler.handleSucceededResult(
                            dispute, result, providerData, invoicePayment.getLastTransactionInfo());
                    case STATUS_FAIL -> disputeStatusResultHandler.handleFailedResult(dispute, result);
                    case STATUS_PENDING -> disputeStatusResultHandler.handlePendingResult(dispute, providerData);
                    default -> throw new IllegalArgumentException(result.getSetField().getFieldName());
                }
            };
            var providerDispute = providerDisputeDao.get(dispute.getId());
            var checkDisputeStatusByRemoteClient = (Runnable) () -> finishCheckDisputeStatusResult.accept(
                    remoteClient.checkDisputeStatus(dispute, providerDispute, providerData, invoicePayment.getLastTransactionInfo()));
            checkDisputeStatusByRemoteClient(dispute, checkDisputeStatusByRemoteClient);
        } catch (NotFoundException ex) {
            log.error("NotFound when handle PendingDisputesService.callPendingDisputeRemotely, type={}", ex.getType(), ex);
            switch (ex.getType()) {
                case INVOICE -> disputeStatusResultHandler.handleFailedResult(dispute, ErrorMessage.INVOICE_NOT_FOUND);
                case PAYMENT -> disputeStatusResultHandler.handleFailedResult(dispute, ErrorMessage.PAYMENT_NOT_FOUND);
                case PROVIDERDISPUTE -> disputeStatusResultHandler.handleProviderDisputeNotFound(
                        dispute, getProviderData(dispute));
                case DISPUTE -> log.debug("Dispute locked {}", dispute);
                default -> throw ex;
            }
        } catch (PoolingExpiredException ex) {
            log.error("PoolingExpired when handle PendingDisputesService.callPendingDisputeRemotely", ex);
            disputeStatusResultHandler.handlePoolingExpired(dispute);
        } catch (CapturedPaymentException ex) {
            log.info("CapturedPaymentException when handle PendingDisputesService.callPendingDisputeRemotely", ex);
            disputeStatusResultHandler.handleSucceededResult(dispute, getChangedAmount(ex.getInvoicePayment().getPayment()));
        } catch (InvoicingPaymentStatusRestrictionsException ex) {
            log.error("InvoicingPaymentRestrictionStatus when handle PendingDisputesService.callPendingDisputeRemotely", ex);
            disputeStatusResultHandler.handleFailedResult(dispute, PaymentStatusValidator.getInvoicingPaymentStatusRestrictionsErrorReason(ex));
        } catch (DisputeStatusWasUpdatedByAnotherThreadException ex) {
            log.debug("DisputeStatusWasUpdatedByAnotherThread when handle CreatedDisputesService.callPendingDisputeRemotely", ex);
        }
    }

    private void checkDisputeStatusByRemoteClient(Dispute dispute, Runnable checkDisputeStatusByRemoteClient) {
        woodyRuntimeExceptionCatcher.catchUnexpectedResultMapping(
                checkDisputeStatusByRemoteClient,
                ex -> disputeStatusResultHandler.handleUnexpectedResultMapping(dispute, ex));
    }

    private ProviderData getProviderData(Dispute dispute) {
        return providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
    }
}
