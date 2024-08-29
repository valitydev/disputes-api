package dev.vality.disputes.schedule.service;

import dev.vality.disputes.ProviderDisputesServiceSrv;
import dev.vality.disputes.config.properties.AdaptersConnectionProperties;
import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderIfaceBuilder {

    private final ProviderRouting providerRouting;
    private final AdaptersConnectionProperties adaptersConnectionProperties;

    public ProviderDisputesServiceSrv.Iface build(Map<String, String> options, String url) {
        return build(providerRouting.getRouteUrl(options, url));
    }

    private ProviderDisputesServiceSrv.Iface build(String url) {
        log.info("Creating new client for url: {}", url);
        return new THSpawnClientBuilder()
                .withNetworkTimeout((int) TimeUnit.SECONDS.toMillis(adaptersConnectionProperties.getTimeoutSec()))
                .withAddress(URI.create(url))
                .build(ProviderDisputesServiceSrv.Iface.class);
    }
}
