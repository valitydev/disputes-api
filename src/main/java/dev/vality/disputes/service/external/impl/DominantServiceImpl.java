package dev.vality.disputes.service.external.impl;

import dev.vality.damsel.domain.*;
import dev.vality.disputes.exception.NotFoundException;
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

    @Override
    public Currency getCurrency(CurrencyRef currencyRef) throws NotFoundException {
        return dominantCacheService.getCurrency(currencyRef);
    }

    @Async
    @Override
    public CompletableFuture<Terminal> getTerminal(TerminalRef terminalRef) {
        try {
            var terminal = dominantCacheService.getTerminal(terminalRef);
            return CompletableFuture.completedFuture(terminal);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async
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
