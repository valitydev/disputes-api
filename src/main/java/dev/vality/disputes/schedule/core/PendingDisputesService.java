package dev.vality.disputes.schedule.core;

import dev.vality.disputes.constant.ErrorMessage;
import dev.vality.disputes.dao.ProviderDisputeDao;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.DisputeStatusWasUpdatedByAnotherThreadException;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.exception.PoolingExpiredException;
import dev.vality.disputes.polling.PollingInfoService;
import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.schedule.catcher.WoodyRuntimeExceptionCatcher;
import dev.vality.disputes.schedule.client.RemoteClient;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.result.DisputeStatusResultHandler;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.service.DisputesService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.disputes.utils.PaymentStatusValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

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
    public List<Dispute> getPendingDisputesForUpdateSkipLocked(int batchSize) {
        return disputesService.getPendingDisputesForUpdateSkipLocked(batchSize);
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
                    case STATUS_SUCCESS -> disputeStatusResultHandler.handleStatusSuccess(dispute, result);
                    case STATUS_FAIL -> disputeStatusResultHandler.handleStatusFail(dispute, result);
                    case STATUS_PENDING -> disputeStatusResultHandler.handleStatusPending(dispute, providerData);
                    default -> throw new IllegalArgumentException(result.getSetField().getFieldName());
                }
            };
            var providerDispute = providerDisputeDao.get(dispute.getId());
            var checkDisputeStatusByRemoteClient = (Runnable) () -> finishCheckDisputeStatusResult.accept(
                    remoteClient.checkDisputeStatus(dispute, providerDispute, providerData));
            checkDisputeStatusByRemoteClient(dispute, checkDisputeStatusByRemoteClient);
        } catch (NotFoundException ex) {
            log.error("NotFound when handle PendingDisputesService.callPendingDisputeRemotely, type={}", ex.getType(), ex);
            switch (ex.getType()) {
                case INVOICE -> disputeStatusResultHandler.handleFailResult(dispute, ErrorMessage.INVOICE_NOT_FOUND);
                case PAYMENT -> disputeStatusResultHandler.handleFailResult(dispute, ErrorMessage.PAYMENT_NOT_FOUND);
                case PROVIDERDISPUTE -> disputeStatusResultHandler.handleProviderDisputeNotFound(
                        dispute, getProviderData(dispute));
                case DISPUTE -> log.debug("Dispute locked {}", dispute);
                default -> throw ex;
            }
        } catch (PoolingExpiredException ex) {
            log.error("PoolingExpired when handle PendingDisputesService.callPendingDisputeRemotely", ex);
            disputeStatusResultHandler.handlePoolingExpired(dispute);
        } catch (InvoicingPaymentStatusRestrictionsException ex) {
            log.error("InvoicingPaymentRestrictionStatus when handle PendingDisputesService.callCreateDisputeRemotely", ex);
            disputeStatusResultHandler.handleFailResult(dispute, PaymentStatusValidator.getInvoicingPaymentStatusRestrictionsErrorReason(ex));
        } catch (DisputeStatusWasUpdatedByAnotherThreadException ex) {
            log.debug("DisputeStatusWasUpdatedByAnotherThread when handle CreatedDisputesService.callCreateDisputeRemotely", ex);
        }
    }

    private void checkDisputeStatusByRemoteClient(Dispute dispute, Runnable checkDisputeStatusByRemoteClient) {
        woodyRuntimeExceptionCatcher.catchUnexpectedResultMapping(
                checkDisputeStatusByRemoteClient,
                e -> disputeStatusResultHandler.handleUnexpectedResultMapping(dispute, e));
    }

    private ProviderData getProviderData(Dispute dispute) {
        return providerDataService.getProviderData(dispute.getProviderId(), dispute.getTerminalId());
    }
}
