package dev.vality.disputes.service.external.impl;

import dev.vality.damsel.domain.*;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.service.external.impl.dominant.DominantCacheServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DominantServiceImpl implements DominantService {

    private final DominantCacheServiceImpl dominantCacheService;

    @Async("asyncDominantServiceExecutor")
    @Override
    public CompletableFuture<Currency> getCurrency(CurrencyRef currencyRef) {
        try {
            var currency = dominantCacheService.getCurrency(currencyRef);
            return CompletableFuture.completedFuture(currency);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async("asyncDominantServiceExecutor")
    @Override
    public CompletableFuture<Terminal> getTerminal(TerminalRef terminalRef) {
        try {
            var terminal = dominantCacheService.getTerminal(terminalRef);
            return CompletableFuture.completedFuture(terminal);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async("asyncDominantServiceExecutor")
    @Override
    public CompletableFuture<ProxyDefinition> getProxy(ProviderRef providerRef) {
        try {
            var provider = dominantCacheService.getProvider(providerRef);
            var proxy = dominantCacheService.getProxy(provider.getProxy().getRef());
            return CompletableFuture.completedFuture(proxy);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
