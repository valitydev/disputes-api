package dev.vality.disputes.provider.payments.service;

import dev.vality.disputes.callback.ProviderPaymentsServiceSrv;
import dev.vality.disputes.config.properties.AdaptersConnectionProperties;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"AbbreviationAsWordInName", "LineLength"})
public class ProviderPaymentsIfaceBuilder {

    private final AdaptersConnectionProperties adaptersConnectionProperties;

    @Cacheable(value = "providerPayments", key = "#root.args[0]", cacheManager = "providerPaymentsCacheManager")
    public ProviderPaymentsServiceSrv.Iface buildTHSpawnClient(String routeUrl) {
        log.info("Creating new client for url: {}", routeUrl);
        return new THSpawnClientBuilder()
                .withNetworkTimeout((int) TimeUnit.SECONDS.toMillis(adaptersConnectionProperties.getTimeoutSec()))
                .withAddress(URI.create(routeUrl))
                .build(ProviderPaymentsServiceSrv.Iface.class);
    }
}
