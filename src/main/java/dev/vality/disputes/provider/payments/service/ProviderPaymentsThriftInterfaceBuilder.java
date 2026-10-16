package dev.vality.disputes.provider.payments.service;

import dev.vality.disputes.config.properties.AdaptersConnectionProperties;
import dev.vality.disputes.remote.BudgetedClientFactory;
import dev.vality.provider.payments.ProviderPaymentsServiceSrv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderPaymentsThriftInterfaceBuilder {

    private final AdaptersConnectionProperties adaptersConnectionProperties;
    private final BudgetedClientFactory budgetedClientFactory;

    @Cacheable(value = "providerPayments", key = "#root.args[0]", cacheManager = "providerPaymentsCacheManager")
    public ProviderPaymentsServiceSrv.Iface buildWoodyClient(String routeUrl) {
        log.info("Creating new client for url: {}", routeUrl);
        return budgetedClientFactory.spawnClientBuilder()
                .withNetworkTimeout((int) TimeUnit.SECONDS.toMillis(adaptersConnectionProperties.getTimeoutSec()))
                .withAddress(URI.create(routeUrl))
                .build(ProviderPaymentsServiceSrv.Iface.class);
    }
}
