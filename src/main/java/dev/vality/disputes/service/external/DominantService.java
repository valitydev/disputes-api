package dev.vality.disputes.service.external;

import dev.vality.damsel.domain.*;

public interface DominantService {

    Currency getCurrency(CurrencyRef currencyRef);

    Terminal getTerminal(TerminalRef terminalRef);

    ProxyDefinition getProxy(ProxyRef proxyRef);

    Provider getProvider(ProviderRef providerRef);

    ShopConfig getShop(ShopConfigRef shopConfigRef);

}
