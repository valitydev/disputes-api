package dev.vality.disputes.provider.payments.callback;

import dev.vality.damsel.domain.Currency;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.api.service.PaymentParamsBuilder;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsRouting;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.security.AccessService;
import dev.vality.disputes.utils.PaymentStatusValidator;
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
@SuppressWarnings({"LineLength"})
public class ProviderPaymentsCallbackHandler implements ProviderPaymentsCallbackServiceSrv.Iface {

    private final AccessService accessService;
    private final PaymentParamsBuilder paymentParamsBuilder;
    private final ProviderDataService providerDataService;
    private final ProviderPaymentsRouting providerPaymentsRouting;
    private final ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;
    private final ProviderCallbackDao providerCallbackDao;

    @Value("${provider.payments.isProviderCallbackEnabled}")
    private boolean enabled;

    @Override
    @Transactional
    public void createAdjustmentWhenFailedPaymentSuccess(ProviderPaymentsCallbackParams callback) throws TException {
        log.info("Got providerPaymentsCallbackParams {}", callback);
        if (!enabled) {
            return;
        }
        if (callback.getInvoiceId().isEmpty()) {
            log.info("InvoiceId should be set, finish");
            return;
        }
        try {
            var accessData = accessService.approveUserAccess(callback.getInvoiceId().get(), callback.getPaymentId().get(), false);
            log.info("Got accessData {}", accessData);
            // validate
            PaymentStatusValidator.checkStatus(accessData.getPayment());
            var paymentParams = paymentParamsBuilder.buildGeneralPaymentContext(accessData);
            log.info("Got paymentParams {}", paymentParams);
            var providerData = providerDataService.getProviderData(paymentParams.getProviderId(), paymentParams.getTerminalId());
            providerPaymentsRouting.initRouteUrl(providerData);
            var transactionContext = getTransactionContext(paymentParams);
            var remoteClient = providerPaymentsThriftInterfaceBuilder.buildWoodyClient(providerData.getRouteUrl());
            var paymentStatusResult = remoteClient.checkPaymentStatus(transactionContext, getCurrency(paymentParams));
            log.info("Called remoteClient.checkPaymentStatus {} {}", transactionContext, paymentStatusResult);
            if (paymentStatusResult.isSuccess()) {
                var providerCallback = new ProviderCallback();
                providerCallback.setInvoiceId(paymentParams.getInvoiceId());
                providerCallback.setPaymentId(paymentParams.getPaymentId());
                providerCallback.setChangedAmount(paymentStatusResult.getChangedAmount().orElse(null));
                providerCallback.setAmount(paymentParams.getInvoiceAmount());
                providerCallbackDao.save(providerCallback);
                log.info("Saved providerCallback, finish {}", providerCallback);
            } else {
                log.info("remoteClient.checkPaymentStatus result was skipped by failed status, finish");
            }
        } catch (NotFoundException ex) {
            log.warn("NotFound when handle ProviderPaymentsCallbackParams, type={}", ex.getType(), ex);
        } catch (InvoicingPaymentStatusRestrictionsException ex) {
            log.error("InvoicingPaymentRestrictionStatus when handle ProviderPaymentsCallbackParams", ex);
        } catch (Throwable ex) {
            log.warn("Failed to handle ProviderPaymentsCallbackParams", ex);
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
