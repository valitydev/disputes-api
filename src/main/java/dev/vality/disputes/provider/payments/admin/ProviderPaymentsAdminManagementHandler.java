package dev.vality.disputes.provider.payments.admin;

import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.provider.payments.ApproveParamsRequest;
import dev.vality.provider.payments.CancelParamsRequest;
import dev.vality.provider.payments.ProviderPaymentsAdminManagementServiceSrv;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ParameterName", "LineLength"})
public class ProviderPaymentsAdminManagementHandler implements ProviderPaymentsAdminManagementServiceSrv.Iface {

    private final ProviderCallbackDao providerCallbackDao;

    @Override
    @Transactional
    public void cancel(CancelParamsRequest cancelParamsRequest) throws TException {
        if (cancelParamsRequest.isCancelAll()) {
            var batch = providerCallbackDao.getAllPendingProviderCallbacksForUpdateSkipLocked().stream()
                    .peek(providerCallback -> setCancelled(cancelParamsRequest, providerCallback))
                    .toList();
            log.info("batch by cancelParamsRequest {}", batch);
            providerCallbackDao.updateBatch(batch);
        } else if (cancelParamsRequest.getCancelParams().isPresent()) {
            var invoiceDataSet = cancelParamsRequest.getCancelParams().get().stream()
                    .map(cancelParams -> InvoiceData.builder()
                            .invoiceId(cancelParams.getInvoiceId())
                            .paymentId(cancelParams.getPaymentId())
                            .build())
                    .collect(Collectors.toSet());
            var batch = providerCallbackDao.getAllPendingProviderCallbacksForUpdateSkipLocked().stream()
                    .filter(providerCallback -> invoiceDataSet.contains(InvoiceData.builder()
                            .invoiceId(providerCallback.getInvoiceId())
                            .paymentId(providerCallback.getPaymentId())
                            .build()))
                    .peek(providerCallback -> setCancelled(cancelParamsRequest, providerCallback))
                    .toList();
            log.info("batch by cancelParamsRequest {}", batch);
            providerCallbackDao.updateBatch(batch);
        }
    }

    @Override
    @Transactional
    public void approve(ApproveParamsRequest approveParamsRequest) throws TException {
        if (approveParamsRequest.isApproveAll()) {
            var batch = providerCallbackDao.getAllPendingProviderCallbacksForUpdateSkipLocked().stream()
                    .peek(providerCallback -> setReadyToCreateAdjustment(approveParamsRequest, providerCallback))
                    .toList();
            log.info("batch by approveParamsRequest {}", batch);
            providerCallbackDao.updateBatch(batch);
        } else if (approveParamsRequest.getApproveParams().isPresent()) {
            var invoiceDataSet = approveParamsRequest.getApproveParams().get().stream()
                    .map(approveParams -> InvoiceData.builder()
                            .invoiceId(approveParams.getInvoiceId())
                            .paymentId(approveParams.getPaymentId())
                            .build())
                    .collect(Collectors.toSet());
            var batch = providerCallbackDao.getAllPendingProviderCallbacksForUpdateSkipLocked().stream()
                    .filter(providerCallback -> invoiceDataSet.contains(InvoiceData.builder()
                            .invoiceId(providerCallback.getInvoiceId())
                            .paymentId(providerCallback.getPaymentId())
                            .build()))
                    .peek(providerCallback -> setReadyToCreateAdjustment(approveParamsRequest, providerCallback))
                    .toList();
            log.info("batch by approveParamsRequest {}", batch);
            providerCallbackDao.updateBatch(batch);
        }
    }

    private void setCancelled(CancelParamsRequest cancelParamsRequest, ProviderCallback providerCallback) {
        if (cancelParamsRequest.getCancelReason().isPresent()) {
            providerCallback.setErrorReason(cancelParamsRequest.getCancelReason().orElse(null));
        }
        providerCallback.setStatus(ProviderPaymentsStatus.cancelled);
    }

    private void setReadyToCreateAdjustment(ApproveParamsRequest approveParamsRequest, ProviderCallback providerCallback) {
        if (approveParamsRequest.getApproveReason().isPresent()) {
            providerCallback.setApproveReason(approveParamsRequest.getApproveReason().orElse(null));
        }
        providerCallback.setStatus(ProviderPaymentsStatus.create_adjustment);
        providerCallback.setSkipCallHgForCreateAdjustment(false);
    }

    @Data
    @Builder
    @EqualsAndHashCode
    public static class InvoiceData {
        @EqualsAndHashCode.Include
        private String invoiceId;
        @EqualsAndHashCode.Include
        private String paymentId;
    }
}
