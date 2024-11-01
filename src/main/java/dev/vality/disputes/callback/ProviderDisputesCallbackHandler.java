package dev.vality.disputes.callback;

import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.api.service.PaymentParamsBuilder;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderCallbackDao;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.TransactionContext;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.schedule.service.ProviderIfaceBuilder;
import dev.vality.disputes.schedule.service.ProviderRouting;
import dev.vality.disputes.security.AccessData;
import dev.vality.disputes.security.AccessService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static dev.vality.disputes.api.service.ApiDisputesService.DISPUTE_PENDING;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class ProviderDisputesCallbackHandler implements ProviderDisputesCallbackServiceSrv.Iface {

    private final DisputeDao disputeDao;
    private final AccessService accessService;
    private final PaymentParamsBuilder paymentParamsBuilder;
    private final ProviderDataService providerDataService;
    private final ProviderRouting providerRouting;
    private final ProviderIfaceBuilder providerIfaceBuilder;
    private final ProviderCallbackDao providerCallbackDao;

    @Value("${dispute.isProviderCallbackEnabled}")
    private boolean enabled;

    @Override
    @Transactional
    public void createAdjustmentIfPaymentSuccess(DisputeCallbackParams disputeCallbackParams) throws TException {
        log.info("disputeCallbackParams {}", disputeCallbackParams);
        if (!enabled) {
            return;
        }
        var invoiceData = disputeCallbackParams.getDisputeID()
                .map(s -> disputeDao.getDisputeForUpdateSkipLocked(UUID.fromString(s)))
                .map(dispute -> InvoiceData.builder()
                        .invoiceId(dispute.getInvoiceId())
                        .paymentId(dispute.getPaymentId())
                        .dispute(dispute)
                        .build())
                .or(() -> disputeCallbackParams.getInvoiceId()
                        .map(s -> disputeDao.getDisputesForUpdateSkipLocked(s, disputeCallbackParams.getPaymentId().get()))
                        .flatMap(disputes -> disputes.stream()
                                .filter(dispute -> DISPUTE_PENDING.contains(dispute.getStatus()))
                                .findFirst())
                        .map(dispute -> InvoiceData.builder()
                                .invoiceId(dispute.getInvoiceId())
                                .paymentId(dispute.getPaymentId())
                                .dispute(dispute)
                                .build())
                        .or(() -> disputeCallbackParams.getInvoiceId()
                                .map(s -> InvoiceData.builder()
                                        .invoiceId(s)
                                        .paymentId(disputeCallbackParams.getPaymentId().get())
                                        .build())))
                .orElse(null);
        log.info("invoiceData {}", invoiceData);
        if (invoiceData == null || invoiceData.getInvoiceId() == null) {
            return;
        }
        var accessData = getAccessData(invoiceData);
        if (accessData == null) {
            return;
        }
        if (!accessData.getPayment().getPayment().getStatus().isSetFailed()) {
            return;
        }
        var paymentParams = getPaymentParams(accessData);
        if (paymentParams == null) {
            return;
        }
        var providerData = providerDataService.getProviderData(paymentParams.getProviderId(), paymentParams.getTerminalId());
        providerRouting.initRouteUrl(providerData);
        var remoteClient = providerIfaceBuilder.buildTHSpawnClient(providerData.getRouteUrl());
        var transactionContext = new TransactionContext();
        transactionContext.setProviderTrxId(paymentParams.getProviderTrxId());
        transactionContext.setInvoiceId(paymentParams.getInvoiceId());
        transactionContext.setPaymentId(paymentParams.getPaymentId());
        transactionContext.setTerminalOptions(paymentParams.getOptions());
        log.info("call remoteClient.isPaymentSuccess {}", transactionContext);
        try {
            var paymentStatusResult = remoteClient.checkPaymentStatus(transactionContext);
            if (paymentStatusResult.isSuccess()) {
                var providerCallback = new ProviderCallback();
                providerCallback.setInvoiceId(paymentParams.getInvoiceId());
                providerCallback.setPaymentId(paymentParams.getPaymentId());
                providerCallback.setChangedAmount(paymentStatusResult.getChangedAmount().orElse(null));
                providerCallbackDao.save(providerCallback);
                log.info("providerCallback {}", providerCallback);
            }
        } catch (TException e) {
            log.warn("remoteClient.isPaymentSuccess error", e);
        }
    }

    private AccessData getAccessData(InvoiceData invoiceData) {
        try {
            var accessData = accessService.approveUserAccess(invoiceData.getInvoiceId(), invoiceData.getPaymentId(), false);
            log.info("accessData {}", accessData);
            return accessData;
        } catch (Throwable e) {
            log.warn("accessData error", e);
            return null;
        }
    }

    private PaymentParams getPaymentParams(AccessData accessData) {
        try {
            var paymentParams = paymentParamsBuilder.buildGeneralPaymentContext(accessData);
            log.info("paymentParams {}", paymentParams);
            return paymentParams;
        } catch (Throwable e) {
            log.warn("paymentParams error", e);
            return null;
        }
    }

    @Data
    @Builder
    public static class InvoiceData {
        private String invoiceId;
        private String paymentId;
        private Dispute dispute;
    }
}
