package dev.vality.disputes.callback;

import dev.vality.damsel.domain.Currency;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.api.service.PaymentParamsBuilder;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.dao.ProviderCallbackDao;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.schedule.service.ProviderDataService;
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
public class ProviderPaymentsCallbackHandler implements ProviderPaymentsCallbackServiceSrv.Iface {

    private final DisputeDao disputeDao;
    private final AccessService accessService;
    private final PaymentParamsBuilder paymentParamsBuilder;
    private final ProviderDataService providerDataService;
    private final ProviderPaymentsRouting providerPaymentsRouting;
    private final ProviderPaymentsIfaceBuilder providerPaymentsIfaceBuilder;
    private final ProviderCallbackDao providerCallbackDao;

    @Value("${dispute.isProviderCallbackEnabled}")
    private boolean enabled;

    @Override
    @Transactional
    public void createAdjustmentWhenFailedPaymentSuccess(ProviderPaymentsCallbackParams providerPaymentsCallbackParams) throws TException {
        log.info("providerPaymentsCallbackParams {}", providerPaymentsCallbackParams);
        if (!enabled) {
            return;
        }
        try {
            var invoiceData = providerPaymentsCallbackParams.getDisputeID()
                    .map(s -> disputeDao.getDisputeForUpdateSkipLocked(UUID.fromString(s)))
                    .map(dispute -> InvoiceData.builder()
                            .invoiceId(dispute.getInvoiceId())
                            .paymentId(dispute.getPaymentId())
                            .dispute(dispute)
                            .build())
                    .or(() -> providerPaymentsCallbackParams.getInvoiceId()
                            .map(s -> disputeDao.getDisputesForUpdateSkipLocked(s, providerPaymentsCallbackParams.getPaymentId().get()))
                            .flatMap(disputes -> disputes.stream()
                                    .filter(dispute -> DISPUTE_PENDING.contains(dispute.getStatus()))
                                    .findFirst())
                            .map(dispute -> InvoiceData.builder()
                                    .invoiceId(dispute.getInvoiceId())
                                    .paymentId(dispute.getPaymentId())
                                    .dispute(dispute)
                                    .build())
                            .or(() -> providerPaymentsCallbackParams.getInvoiceId()
                                    .map(s -> InvoiceData.builder()
                                            .invoiceId(s)
                                            .paymentId(providerPaymentsCallbackParams.getPaymentId().get())
                                            .build())))
                    .orElse(null);
            log.info("invoiceData {}", invoiceData);
            if (invoiceData == null || invoiceData.getInvoiceId() == null) {
                return;
            }
            var accessData = accessService.approveUserAccess(invoiceData.getInvoiceId(), invoiceData.getPaymentId(), false);
            log.info("accessData {}", accessData);
            if (!accessData.getPayment().getPayment().getStatus().isSetFailed()) {
                return;
            }
            var paymentParams = paymentParamsBuilder.buildGeneralPaymentContext(accessData);
            log.info("paymentParams {}", paymentParams);
            var providerData = providerDataService.getProviderData(paymentParams.getProviderId(), paymentParams.getTerminalId());
            providerPaymentsRouting.initRouteUrl(providerData);
            var transactionContext = getTransactionContext(paymentParams);
            var remoteClient = providerPaymentsIfaceBuilder.buildTHSpawnClient(providerData.getRouteUrl());
            log.info("call remoteClient.isPaymentSuccess {}", transactionContext);
            var paymentStatusResult = remoteClient.checkPaymentStatus(transactionContext, getCurrency(paymentParams));
            if (paymentStatusResult.isSuccess()) {
                var providerCallback = new ProviderCallback();
                providerCallback.setInvoiceId(paymentParams.getInvoiceId());
                providerCallback.setPaymentId(paymentParams.getPaymentId());
                providerCallback.setChangedAmount(paymentStatusResult.getChangedAmount().orElse(null));
                providerCallback.setAmount(paymentParams.getInvoiceAmount());
                providerCallbackDao.save(providerCallback);
                log.info("providerCallback {}", providerCallback);
            }
        } catch (TException e) {
            log.warn("remoteClient.isPaymentSuccess error", e);
        }
    }

    private static TransactionContext getTransactionContext(PaymentParams paymentParams) {
        var transactionContext = new TransactionContext();
        transactionContext.setProviderTrxId(paymentParams.getProviderTrxId());
        transactionContext.setInvoiceId(paymentParams.getInvoiceId());
        transactionContext.setPaymentId(paymentParams.getPaymentId());
        transactionContext.setTerminalOptions(paymentParams.getOptions());
        return transactionContext;
    }

    private static Currency getCurrency(PaymentParams paymentParams) {
        var currency = new Currency();
        currency.setName(paymentParams.getCurrencyName());
        currency.setSymbolicCode(paymentParams.getCurrencySymbolicCode());
        currency.setNumericCode(paymentParams.getCurrencyNumericCode().shortValue());
        currency.setExponent(paymentParams.getCurrencyExponent().shortValue());
        return currency;
    }

    @Data
    @Builder
    public static class InvoiceData {
        private String invoiceId;
        private String paymentId;
        private Dispute dispute;
    }
}
