package dev.vality.disputes.provider.payments.client;

import dev.vality.damsel.domain.Currency;
import dev.vality.disputes.domain.tables.pojos.RetryProviderPaymentCheckStatus;
import dev.vality.disputes.provider.payments.dao.RetryProviderPaymentCheckStatusDao;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsRouting;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.provider.payments.PaymentStatusResult;
import dev.vality.provider.payments.TransactionContext;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class ProviderPaymentsRemoteClient {

    private final ProviderPaymentsRouting providerPaymentsRouting;
    private final ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;
    private final RetryProviderPaymentCheckStatusDao retryProviderPaymentCheckStatusDao;

    @SneakyThrows
    public PaymentStatusResult checkPaymentStatus(TransactionContext transactionContext, Currency currency, ProviderData providerData) {
        try {
            providerPaymentsRouting.initRouteUrl(providerData);
            var remoteClient = providerPaymentsThriftInterfaceBuilder.buildWoodyClient(providerData.getRouteUrl());
            var paymentStatusResult = remoteClient.checkPaymentStatus(transactionContext, currency);
            log.info("Called remoteClient.checkPaymentStatus {} {}", transactionContext, paymentStatusResult);
            return paymentStatusResult;
        } catch (TException ex) {
            log.warn("Failed when handle ProviderPaymentsCallbackHandler.callCheckPaymentStatusRemotely, save invoice for future retry", ex);
            var retryProviderPaymentCheckStatus = new RetryProviderPaymentCheckStatus();
            retryProviderPaymentCheckStatus.setInvoiceId(transactionContext.getInvoiceId());
            retryProviderPaymentCheckStatus.setPaymentId(transactionContext.getPaymentId());
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
}
