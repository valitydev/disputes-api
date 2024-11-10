package dev.vality.disputes.provider.payments.callback;

import dev.vality.damsel.domain.Currency;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.api.service.PaymentParamsBuilder;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.domain.tables.pojos.RetryProviderPaymentCheckStatus;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.dao.RetryProviderPaymentCheckStatusDao;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsRouting;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.security.AccessService;
import dev.vality.disputes.utils.PaymentStatusValidator;
import dev.vality.provider.payments.PaymentStatusResult;
import dev.vality.provider.payments.ProviderPaymentsCallbackParams;
import dev.vality.provider.payments.ProviderPaymentsCallbackServiceSrv;
import dev.vality.provider.payments.TransactionContext;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
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
    private final RetryProviderPaymentCheckStatusDao retryProviderPaymentCheckStatusDao;

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
            var paymentStatusResult = callCheckPaymentStatusRemotely(providerData, paymentParams);
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
        } catch (Throwable ex) {
            log.warn("Failed to handle ProviderPaymentsCallbackParams", ex);
        }
    }

    @SneakyThrows
    private PaymentStatusResult callCheckPaymentStatusRemotely(ProviderData providerData, PaymentParams paymentParams) {
        try {
            providerPaymentsRouting.initRouteUrl(providerData);
            var transactionContext = buildTransactionContext(paymentParams);
            var remoteClient = providerPaymentsThriftInterfaceBuilder.buildWoodyClient(providerData.getRouteUrl());
            var paymentStatusResult = remoteClient.checkPaymentStatus(transactionContext, buildCurrency(paymentParams));
            log.info("Called remoteClient.checkPaymentStatus {} {}", transactionContext, paymentStatusResult);
            return paymentStatusResult;
        } catch (TException ex) {
            log.warn("Failed when handle ProviderPaymentsCallbackHandler.callCheckPaymentStatusRemotely, save invoice for future retry", ex);
            var retryProviderPaymentCheckStatus = new RetryProviderPaymentCheckStatus();
            retryProviderPaymentCheckStatus.setInvoiceId(paymentParams.getInvoiceId());
            retryProviderPaymentCheckStatus.setPaymentId(paymentParams.getPaymentId());
            // todo добавить шедулатор, вначале проверяет PaymentStatusValidator.checkStatus(accessData.getPayment());
            //  потому что, если они будут лежать в бд вечность (это касается и ProviderCallback)
            //  то статусы платежей успеют обновится (на сверках, еще как то, типа по запросам мерчей)
            //  и нужно будет зафиналить в фейлы в ProviderCallback (тк уже не актуально) + удалить из бд RetryProviderPaymentCheckStatus
            //  тк записи в бд RetryProviderPaymentCheckStatus временные, после финализации уже не нужны
            //  PS еще здесь может оказаться запрос еще у провайдера не реализована апи checkStatus и мы тогда не потеряем данные
            retryProviderPaymentCheckStatusDao.save(retryProviderPaymentCheckStatus);
            throw ex;
        }
    }

    private TransactionContext buildTransactionContext(PaymentParams paymentParams) {
        var transactionContext = new TransactionContext();
        transactionContext.setProviderTrxId(paymentParams.getProviderTrxId());
        transactionContext.setInvoiceId(paymentParams.getInvoiceId());
        transactionContext.setPaymentId(paymentParams.getPaymentId());
        transactionContext.setTerminalOptions(paymentParams.getOptions());
        return transactionContext;
    }

    private Currency buildCurrency(PaymentParams paymentParams) {
        var currency = new Currency();
        currency.setName(paymentParams.getCurrencyName());
        currency.setSymbolicCode(paymentParams.getCurrencySymbolicCode());
        currency.setNumericCode(paymentParams.getCurrencyNumericCode().shortValue());
        currency.setExponent(paymentParams.getCurrencyExponent().shortValue());
        return currency;
    }
}
