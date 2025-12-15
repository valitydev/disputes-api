package dev.vality.disputes.service.external.impl.dominant;

import dev.vality.damsel.domain.*;
import dev.vality.disputes.service.external.DominantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DominantAsyncService {

    private final DominantService dominantService;

    @Async("disputesAsyncServiceExecutor")
    public CompletableFuture<Currency> getCurrency(CurrencyRef currencyRef) {
        try {
            var currency = dominantService.getCurrency(currencyRef);
            return CompletableFuture.completedFuture(currency);
        } catch (Throwable ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    @Async("disputesAsyncServiceExecutor")
    public CompletableFuture<Terminal> getTerminal(TerminalRef terminalRef) {
        try {
            var terminal = dominantService.getTerminal(terminalRef);
            return CompletableFuture.completedFuture(terminal);
        } catch (Throwable ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    @Async("disputesAsyncServiceExecutor")
    public CompletableFuture<ProxyDefinition> getProxy(ProxyRef proxyRef) {
        try {
            var proxy = dominantService.getProxy(proxyRef);
            return CompletableFuture.completedFuture(proxy);
        } catch (Throwable ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    @Async("disputesAsyncServiceExecutor")
    public CompletableFuture<Provider> getProvider(ProviderRef providerRef) {
        try {
            var provider = dominantService.getProvider(providerRef);
            return CompletableFuture.completedFuture(provider);
        } catch (Throwable ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    @Async("disputesAsyncServiceExecutor")
    public CompletableFuture<ShopConfig> getShop(ShopConfigRef shopConfigRef) {
        try {
            var shop = dominantService.getShop(shopConfigRef);
            return CompletableFuture.completedFuture(shop);
        } catch (Throwable ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }
}
