package dev.vality.disputes.service.external.impl.dominant;

import dev.vality.damsel.domain.*;
import dev.vality.damsel.domain_config.Reference;
import dev.vality.damsel.domain_config.*;
import dev.vality.disputes.exception.DominantException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.exception.NotFoundException.Type;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class DominantCacheServiceImpl {

    private final RepositoryClientSrv.Iface dominantClient;

    @Cacheable(value = "currencies", key = "#currencyRef.symbolic_code", cacheManager = "currenciesCacheManager")
    public Currency getCurrency(CurrencyRef currencyRef) {
        return getCurrency(currencyRef, Reference.head(new Head()));
    }

    private Currency getCurrency(CurrencyRef currencyRef, Reference revisionReference) {
        log.debug("Trying to get currency, currencyRef='{}', revisionReference='{}'", currencyRef, revisionReference);
        try {
            var reference = new dev.vality.damsel.domain.Reference();
            reference.setCurrency(currencyRef);
            var versionedObject = checkoutObject(revisionReference, reference);
            var currency = versionedObject.getObject().getCurrency().getData();
            log.debug("Currency has been found, currencyRef='{}', revisionReference='{}'",
                    currencyRef, revisionReference);
            return currency;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(String.format("Version not found, currencyRef='%s', revisionReference='%s'", currencyRef, revisionReference), ex, Type.CURRENCY);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get currency, currencyRef='%s', " +
                    "revisionReference='%s'", currencyRef, revisionReference), ex);
        }
    }

    @Cacheable(value = "terminals", key = "#terminalRef.id", cacheManager = "terminalsCacheManager")
    public Terminal getTerminal(TerminalRef terminalRef) {
        return getTerminal(terminalRef, Reference.head(new Head()));
    }

    public Terminal getTerminal(TerminalRef terminalRef, Reference revisionReference) {
        log.debug("Trying to get terminal from dominant, terminalRef='{}', revisionReference='{}'", terminalRef,
                revisionReference);
        try {
            var reference = new dev.vality.damsel.domain.Reference();
            reference.setTerminal(terminalRef);
            var versionedObject = checkoutObject(revisionReference, reference);
            var terminal = versionedObject.getObject().getTerminal().getData();
            log.debug("Terminal has been found, terminalRef='{}', revisionReference='{}'",
                    terminalRef, revisionReference);
            return terminal;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(String.format("Version not found, terminalRef='%s', revisionReference='%s'", terminalRef, revisionReference), ex, Type.TERMINAL);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get terminal, terminalRef='%s'," +
                    " revisionReference='%s'", terminalRef, revisionReference), ex);
        }
    }

    @Cacheable(value = "providers", key = "#providerRef.id", cacheManager = "providersCacheManager")
    public Provider getProvider(ProviderRef providerRef) {
        return getProvider(providerRef, Reference.head(new Head()));
    }

    private Provider getProvider(ProviderRef providerRef, Reference revisionReference) {
        log.debug("Trying to get provider from dominant, providerRef='{}', revisionReference='{}'", providerRef,
                revisionReference);
        try {
            var reference = new dev.vality.damsel.domain.Reference();
            reference.setProvider(providerRef);
            var versionedObject = checkoutObject(revisionReference, reference);
            var provider = versionedObject.getObject().getProvider().getData();
            log.debug("Provider has been found, providerRef='{}', revisionReference='{}'",
                    providerRef, revisionReference);
            return provider;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(String.format("Version not found, providerRef='%s', revisionReference='%s'", providerRef, revisionReference), ex, Type.PROVIDER);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get provider, providerRef='%s'," +
                    " revisionReference='%s'", providerRef, revisionReference), ex);
        }
    }

    @Cacheable(value = "proxies", key = "#proxyRef.id", cacheManager = "proxiesCacheManager")
    public ProxyDefinition getProxy(ProxyRef proxyRef) {
        return getProxy(proxyRef, Reference.head(new Head()));
    }


    private ProxyDefinition getProxy(ProxyRef proxyRef, Reference revisionReference) {
        log.debug("Trying to get proxy from dominant, proxyRef='{}', revisionReference='{}'", proxyRef,
                revisionReference);
        try {
            var reference = new dev.vality.damsel.domain.Reference();
            reference.setProxy(proxyRef);
            var versionedObject = checkoutObject(revisionReference, reference);
            var proxy = versionedObject.getObject().getProxy().getData();
            log.debug("Proxy has been found, proxyRef='{}', revisionReference='{}'", proxyRef, revisionReference);
            return proxy;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(String.format("Version not found, proxyRef='%s', revisionReference='%s'", proxyRef, revisionReference), ex, Type.PROXY);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get proxy, proxyRef='%s', revisionReference='%s'",
                    proxyRef, revisionReference), ex);
        }
    }

    private VersionedObject checkoutObject(Reference revisionReference, dev.vality.damsel.domain.Reference reference) throws TException {
        return dominantClient.checkoutObject(revisionReference, reference);
    }
}
