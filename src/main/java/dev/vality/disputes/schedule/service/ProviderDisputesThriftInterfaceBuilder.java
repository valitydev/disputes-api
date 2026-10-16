package dev.vality.disputes.schedule.service;

import dev.vality.disputes.config.properties.AdaptersConnectionProperties;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ProviderDisputesThriftInterfaceBuilder {

    private final AdaptersConnectionProperties adaptersConnectionProperties;
    private final CloseableHttpClient woodyHttpClient;

    public ProviderDisputesThriftInterfaceBuilder(AdaptersConnectionProperties adaptersConnectionProperties,
            @Qualifier("woodyHttpClient") CloseableHttpClient woodyHttpClient) {
        this.adaptersConnectionProperties = adaptersConnectionProperties;
        this.woodyHttpClient = woodyHttpClient;
    }

    @Cacheable(value = "providerDisputes", key = "#root.args[0]", cacheManager = "providerDisputesCacheManager")
    public ProviderDisputesServiceSrv.Iface buildWoodyClient(String routeUrl) {
        log.info("Creating new client for url: {}", routeUrl);
        return new THSpawnClientBuilder()
                .withHttpClient(woodyHttpClient)
                .withNetworkTimeout((int) TimeUnit.SECONDS.toMillis(adaptersConnectionProperties.getTimeoutSec()))
                .withAddress(URI.create(routeUrl))
                .build(ProviderDisputesServiceSrv.Iface.class);
    }
}
