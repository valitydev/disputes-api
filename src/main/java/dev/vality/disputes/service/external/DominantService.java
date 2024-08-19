package dev.vality.disputes.service.external;

import dev.vality.damsel.domain.*;

import java.util.concurrent.CompletableFuture;

public interface DominantService {

    Currency getCurrency(CurrencyRef currencyRef);

    PaymentService getPaymentService(PaymentServiceRef paymentServiceRef);

    CompletableFuture<Terminal> getTerminal(TerminalRef terminalRef);

    CompletableFuture<Provider> getProvider(ProviderRef providerRef);

    CompletableFuture<ProxyDefinition> getProxy(ProxyRef proxyRef);
}
