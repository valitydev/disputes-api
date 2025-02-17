package dev.vality.disputes.provider.payments.admin;

import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsService;
import dev.vality.provider.payments.ApproveParamsRequest;
import dev.vality.provider.payments.CancelParamsRequest;
import dev.vality.provider.payments.ProviderPaymentsAdminManagementServiceSrv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class ProviderPaymentsAdminManagementHandler implements ProviderPaymentsAdminManagementServiceSrv.Iface {

    private final ProviderCallbackDao providerCallbackDao;
    private final ProviderPaymentsService providerPaymentsService;

    @Override
    @Transactional
    public void cancel(CancelParamsRequest cancelParamsRequest) throws TException {
        log.info("Got ProviderPayments CancelParamsRequest {}", cancelParamsRequest);
        if (cancelParamsRequest.isCancelAll()) {
            var batch = providerCallbackDao.getAllPendingProviderCallbacksForUpdateSkipLocked();
            finishCancelled(batch, cancelParamsRequest);
        } else if (cancelParamsRequest.getCancelParams().isPresent()) {
            var invoicePaymentIds = cancelParamsRequest.getCancelParams().get().stream()
                    .map(cancelParams -> cancelParams.getInvoiceId() + cancelParams.getPaymentId())
                    .collect(Collectors.toSet());
            var batch = providerCallbackDao.getProviderCallbacksForUpdateSkipLocked(invoicePaymentIds);
            finishCancelled(batch, cancelParamsRequest);
        }
        log.info("Finish ProviderPayments CancelParamsRequest {}", cancelParamsRequest);
    }

    @Override
    @Transactional
    public void approve(ApproveParamsRequest approveParamsRequest) throws TException {
        log.info("Got ProviderPayments ApproveParamsRequest {}", approveParamsRequest);
        if (approveParamsRequest.isApproveAll()) {
            var batch = providerCallbackDao.getAllPendingProviderCallbacksForUpdateSkipLocked().stream()
                    .filter(ProviderCallback::getSkipCallHgForCreateAdjustment)
                    .peek(providerCallback -> setReadyToCreateAdjustment(approveParamsRequest, providerCallback))
                    .toList();
            setReadyToCreateAdjustment(batch);
        } else if (approveParamsRequest.getApproveParams().isPresent()) {
            var invoicePaymentIds = approveParamsRequest.getApproveParams().get().stream()
                    .map(approveParams -> approveParams.getInvoiceId() + approveParams.getPaymentId())
                    .collect(Collectors.toSet());
            var batch = providerCallbackDao.getProviderCallbacksForUpdateSkipLocked(invoicePaymentIds).stream()
                    .filter(ProviderCallback::getSkipCallHgForCreateAdjustment)
                    .peek(providerCallback -> setReadyToCreateAdjustment(approveParamsRequest, providerCallback))
                    .toList();
            setReadyToCreateAdjustment(batch);
        }
        log.info("Got ProviderPayments ApproveParamsRequest {}", approveParamsRequest);
    }

    private void finishCancelled(List<ProviderCallback> batch, CancelParamsRequest cancelParamsRequest) {
        log.debug("Batch by ProviderPayments cancelParamsRequest {}", batch.size());
        for (var providerCallback : batch) {
            providerPaymentsService.finishCancelled(providerCallback, cancelParamsRequest.getCancelReason().orElse(null), true);
        }
    }

    private void setReadyToCreateAdjustment(ApproveParamsRequest approveParamsRequest, ProviderCallback providerCallback) {
        if (approveParamsRequest.getApproveReason().isPresent()) {
            providerCallback.setApproveReason(approveParamsRequest.getApproveReason().orElse(null));
        }
        providerCallback.setStatus(ProviderPaymentsStatus.create_adjustment);
        providerCallback.setSkipCallHgForCreateAdjustment(false);
    }

    private void setReadyToCreateAdjustment(List<ProviderCallback> batch) {
        log.debug("Batch by ProviderPayments approveParamsRequest {}", batch.size());
        providerCallbackDao.updateBatch(batch);
    }
}
