package dev.vality.disputes.provider.payments.client;

import dev.vality.damsel.domain.Currency;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsRouting;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsThriftInterfaceBuilder;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.provider.payments.PaymentStatusResult;
import dev.vality.provider.payments.TransactionContext;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class ProviderPaymentsRemoteClient {

    private final ProviderPaymentsRouting providerPaymentsRouting;
    private final ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;

    @SneakyThrows
    public PaymentStatusResult checkPaymentStatus(TransactionContext transactionContext, Currency currency, ProviderData providerData) {
        providerPaymentsRouting.initRouteUrl(providerData);
        var remoteClient = providerPaymentsThriftInterfaceBuilder.buildWoodyClient(providerData.getRouteUrl());
        var paymentStatusResult = remoteClient.checkPaymentStatus(transactionContext, currency);
        log.info("Called remoteClient.checkPaymentStatus {} {}", transactionContext, paymentStatusResult);
        return paymentStatusResult;
    }
}
