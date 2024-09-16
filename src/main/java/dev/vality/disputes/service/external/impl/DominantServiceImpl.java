package dev.vality.disputes.service.external.impl;

import dev.vality.damsel.domain.*;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.service.external.impl.dominant.DominantCacheServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DominantServiceImpl implements DominantService {

    private final DominantCacheServiceImpl dominantCacheService;

    @Override
    public Currency getCurrency(CurrencyRef currencyRef) throws NotFoundException {
        return dominantCacheService.getCurrency(currencyRef);
    }

    @Override
    public Terminal getTerminal(TerminalRef terminalRef) {
        return dominantCacheService.getTerminal(terminalRef);
    }

    @Override
    public ProxyDefinition getProxy(ProviderRef providerRef) {
        var provider = dominantCacheService.getProvider(providerRef);
        return dominantCacheService.getProxy(provider.getProxy().getRef());
    }
}
