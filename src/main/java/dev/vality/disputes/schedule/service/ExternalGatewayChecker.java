package dev.vality.disputes.schedule.service;

import dev.vality.disputes.schedule.model.ProviderData;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalGatewayChecker {

    private final CloseableHttpClient httpClient;
    private final ProviderDisputesRouting providerDisputesRouting;

    @SneakyThrows
    public boolean isProviderDisputesApiNotFound(ProviderData providerData) {
        return httpClient.execute(new HttpGet(getRouteUrl(providerData)), isNotFoundResponse());
    }

    private String getRouteUrl(ProviderData providerData) {
        providerDisputesRouting.initRouteUrl(providerData);
        log.debug("Check adapter connection, routeUrl={}", providerData.getRouteUrl());
        return providerData.getRouteUrl();
    }

    private HttpClientResponseHandler<Boolean> isNotFoundResponse() {
        return response -> {
            log.debug("Check adapter connection, resp={}", response);
            return response.getCode() == HttpStatus.SC_NOT_FOUND;
        };
    }
}
