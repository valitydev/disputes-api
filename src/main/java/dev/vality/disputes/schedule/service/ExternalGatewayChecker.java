package dev.vality.disputes.schedule.service;

import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.woody.api.flow.error.WErrorSource;
import dev.vality.woody.api.flow.error.WErrorType;
import dev.vality.woody.api.flow.error.WRuntimeException;
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
@SuppressWarnings({"ParameterName", "LineLength"})
public class ExternalGatewayChecker {

    private final CloseableHttpClient httpClient;
    private final ProviderRouting providerRouting;

    public boolean isNotProvidersDisputesApiExist(ProviderData providerData, WRuntimeException e) {
        return e.getErrorDefinition() != null
                && e.getErrorDefinition().getGenerationSource() == WErrorSource.EXTERNAL
                && e.getErrorDefinition().getErrorType() == WErrorType.UNEXPECTED_ERROR
                && e.getErrorDefinition().getErrorSource() == WErrorSource.INTERNAL
                && isNotFoundProvidersDisputesApi(providerData);
    }

    @SneakyThrows
    private Boolean isNotFoundProvidersDisputesApi(ProviderData providerData) {
        return httpClient.execute(new HttpGet(getRouteUrl(providerData)), isNotFoundResponse());
    }

    private String getRouteUrl(ProviderData providerData) {
        var routeUrl = providerRouting.getRouteUrl(providerData);
        log.debug("Check adapter connection, routeUrl={}", routeUrl);
        return routeUrl;
    }

    private HttpClientResponseHandler<Boolean> isNotFoundResponse() {
        return response -> {
            log.debug("Check adapter connection, resp={}", response);
            return response.getCode() == HttpStatus.SC_NOT_FOUND;
        };
    }
}
