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
public class ProviderPaymentsRemoteClient {

    private final ProviderPaymentsRouting providerPaymentsRouting;
    private final ProviderPaymentsThriftInterfaceBuilder providerPaymentsThriftInterfaceBuilder;

    @SneakyThrows
    public PaymentStatusResult checkPaymentStatus(TransactionContext transactionContext, Currency currency,
                                                  ProviderData providerData) {
        log.info("Trying to call ProviderPaymentsThriftInterfaceBuilder.checkPaymentStatus() {}", transactionContext);
        providerPaymentsRouting.initRouteUrl(providerData);
        var remoteClient = providerPaymentsThriftInterfaceBuilder.buildWoodyClient(providerData.getRouteUrl());
        return remoteClient.checkPaymentStatus(transactionContext, currency);
    }
}
