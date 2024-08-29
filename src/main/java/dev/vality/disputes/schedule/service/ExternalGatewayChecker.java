package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.ProviderRef;
import dev.vality.damsel.domain.ProxyDefinition;
import dev.vality.damsel.domain.Terminal;
import dev.vality.damsel.domain.TerminalRef;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.service.external.DominantService;
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

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength"})
public class ExternalGatewayChecker {

    private final CloseableHttpClient httpClient;
    private final DominantService dominantService;
    private final ProviderRouting providerRouting;

    public boolean isNotProvidersDisputesApiExist(Dispute dispute, WRuntimeException e) {
        return e.getErrorDefinition() != null
                && e.getErrorDefinition().getGenerationSource() == WErrorSource.EXTERNAL
                && e.getErrorDefinition().getErrorType() == WErrorType.UNEXPECTED_ERROR
                && e.getErrorDefinition().getErrorSource() == WErrorSource.INTERNAL
                && isNotFoundProvidersDisputesApi(dispute);
    }

    @SneakyThrows
    private Boolean isNotFoundProvidersDisputesApi(Dispute dispute) {
        return httpClient.execute(new HttpGet(getRouteUrl(dispute)), isNotFoundResponse());
    }

    @SneakyThrows
    private String getRouteUrl(Dispute dispute) {
        return providerRouting.getRouteUrl(getTerminal(dispute.getTerminalId()).get().getOptions(), getProxy(dispute.getProviderId()).get().getUrl());
    }

    private HttpClientResponseHandler<Boolean> isNotFoundResponse() {
        return response -> response.getCode() == HttpStatus.SC_NOT_FOUND;
    }

    private CompletableFuture<Terminal> getTerminal(Integer terminalId) {
        return dominantService.getTerminal(new TerminalRef(terminalId));
    }

    private CompletableFuture<ProxyDefinition> getProxy(Integer providerId) {
        return dominantService.getProxy(new ProviderRef(providerId));
    }
}
