package dev.vality.disputes.service.external;

import dev.vality.damsel.domain.*;

import java.util.concurrent.CompletableFuture;

public interface DominantService {

    CompletableFuture<Currency> getCurrency(CurrencyRef currencyRef);

    CompletableFuture<Terminal> getTerminal(TerminalRef terminalRef);

    CompletableFuture<ProxyDefinition> getProxy(ProviderRef providerRef);

}
