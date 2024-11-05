package dev.vality.disputes.provider.payments.callback;

import dev.vality.damsel.domain.Currency;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.api.service.PaymentParamsBuilder;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsIfaceBuilder;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsRouting;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.security.AccessService;
import dev.vality.provider.payments.ProviderPaymentsCallbackParams;
import dev.vality.provider.payments.ProviderPaymentsCallbackServiceSrv;
import dev.vality.provider.payments.TransactionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ParameterName", "LineLength", "MissingSwitchDefault"})
public class ProviderPaymentsCallbackHandler implements ProviderPaymentsCallbackServiceSrv.Iface {

    private final AccessService accessService;
    private final PaymentParamsBuilder paymentParamsBuilder;
    private final ProviderDataService providerDataService;
    private final ProviderPaymentsRouting providerPaymentsRouting;
    private final ProviderPaymentsIfaceBuilder providerPaymentsIfaceBuilder;
    private final ProviderCallbackDao providerCallbackDao;

    @Value("${provider.payments.isProviderCallbackEnabled}")
    private boolean enabled;

    @Override
    @Transactional
    public void createAdjustmentWhenFailedPaymentSuccess(ProviderPaymentsCallbackParams providerPaymentsCallbackParams) throws TException {
        log.info("providerPaymentsCallbackParams {}", providerPaymentsCallbackParams);
        if (!enabled) {
            return;
        }
        if (providerPaymentsCallbackParams.getInvoiceId().isEmpty()) {
            return;
        }
        try {
            var accessData = accessService.approveUserAccess(providerPaymentsCallbackParams.getInvoiceId().get(),
                    providerPaymentsCallbackParams.getPaymentId().get(), false);
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
            log.info("call remoteClient.checkPaymentStatus {}", transactionContext);
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
        } catch (Throwable e) {
            log.warn("createAdjustmentWhenFailedPaymentSuccess() error", e);
        }
    }

    private TransactionContext getTransactionContext(PaymentParams paymentParams) {
        var transactionContext = new TransactionContext();
        transactionContext.setProviderTrxId(paymentParams.getProviderTrxId());
        transactionContext.setInvoiceId(paymentParams.getInvoiceId());
        transactionContext.setPaymentId(paymentParams.getPaymentId());
        transactionContext.setTerminalOptions(paymentParams.getOptions());
        return transactionContext;
    }

    private Currency getCurrency(PaymentParams paymentParams) {
        var currency = new Currency();
        currency.setName(paymentParams.getCurrencyName());
        currency.setSymbolicCode(paymentParams.getCurrencySymbolicCode());
        currency.setNumericCode(paymentParams.getCurrencyNumericCode().shortValue());
        currency.setExponent(paymentParams.getCurrencyExponent().shortValue());
        return currency;
    }
}
