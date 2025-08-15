package dev.vality.disputes.service.external.impl.dominant;

import dev.vality.damsel.domain.*;
import dev.vality.damsel.domain_config_v2.*;
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
public class DominantCacheServiceImpl {

    private final RepositoryClientSrv.Iface dominantClient;

    @Cacheable(value = "currencies", key = "#currencyRef.symbolic_code", cacheManager = "currenciesCacheManager")
    public Currency getCurrency(CurrencyRef currencyRef) {
        return getCurrency(currencyRef, VersionReference.head(new Head()));
    }

    private Currency getCurrency(CurrencyRef currencyRef, VersionReference versionReference) {
        log.debug("Trying to get currency, currencyRef='{}', versionReference='{}'", currencyRef, versionReference);
        try {
            var reference = new Reference();
            reference.setCurrency(currencyRef);
            var versionedObject = checkoutObject(versionReference, reference);
            var currency = versionedObject.getObject().getCurrency().getData();
            log.debug("Currency has been found, currencyRef='{}', versionReference='{}'",
                    currencyRef, versionReference);
            return currency;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(
                    String.format("Version not found, currencyRef='%s', versionReference='%s'", currencyRef,
                            versionReference), ex, Type.CURRENCY);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get currency, currencyRef='%s', " +
                    "versionReference='%s'", currencyRef, versionReference), ex);
        }
    }

    @Cacheable(value = "terminals", key = "#terminalRef.id", cacheManager = "terminalsCacheManager")
    public Terminal getTerminal(TerminalRef terminalRef) {
        return getTerminal(terminalRef, VersionReference.head(new Head()));
    }

    public Terminal getTerminal(TerminalRef terminalRef, VersionReference versionReference) {
        log.debug("Trying to get terminal from dominant, terminalRef='{}', versionReference='{}'", terminalRef,
                versionReference);
        try {
            var reference = new Reference();
            reference.setTerminal(terminalRef);
            var versionedObject = checkoutObject(versionReference, reference);
            var terminal = versionedObject.getObject().getTerminal().getData();
            log.debug("Terminal has been found, terminalRef='{}', versionReference='{}'",
                    terminalRef, versionReference);
            return terminal;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(
                    String.format("Version not found, terminalRef='%s', versionReference='%s'", terminalRef,
                            versionReference), ex, Type.TERMINAL);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get terminal, terminalRef='%s'," +
                    " versionReference='%s'", terminalRef, versionReference), ex);
        }
    }

    @Cacheable(value = "providers", key = "#providerRef.id", cacheManager = "providersCacheManager")
    public Provider getProvider(ProviderRef providerRef) {
        return getProvider(providerRef, VersionReference.head(new Head()));
    }

    private Provider getProvider(ProviderRef providerRef, VersionReference versionReference) {
        log.debug("Trying to get provider from dominant, providerRef='{}', versionReference='{}'", providerRef,
                versionReference);
        try {
            var reference = new Reference();
            reference.setProvider(providerRef);
            var versionedObject = checkoutObject(versionReference, reference);
            var provider = versionedObject.getObject().getProvider().getData();
            log.debug("Provider has been found, providerRef='{}', versionReference='{}'",
                    providerRef, versionReference);
            return provider;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(
                    String.format("Version not found, providerRef='%s', versionReference='%s'", providerRef,
                            versionReference), ex, Type.PROVIDER);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get provider, providerRef='%s'," +
                    " versionReference='%s'", providerRef, versionReference), ex);
        }
    }

    @Cacheable(value = "proxies", key = "#proxyRef.id", cacheManager = "proxiesCacheManager")
    public ProxyDefinition getProxy(ProxyRef proxyRef) {
        return getProxy(proxyRef, VersionReference.head(new Head()));
    }


    private ProxyDefinition getProxy(ProxyRef proxyRef, VersionReference versionReference) {
        log.debug("Trying to get proxy from dominant, proxyRef='{}', versionReference='{}'", proxyRef,
                versionReference);
        try {
            var reference = new Reference();
            reference.setProxy(proxyRef);
            var versionedObject = checkoutObject(versionReference, reference);
            var proxy = versionedObject.getObject().getProxy().getData();
            log.debug("Proxy has been found, proxyRef='{}', versionReference='{}'", proxyRef, versionReference);
            return proxy;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(
                    String.format("Version not found, proxyRef='%s', versionReference='%s'", proxyRef,
                            versionReference), ex, Type.PROXY);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get proxy, proxyRef='%s', versionReference='%s'",
                    proxyRef, versionReference), ex);
        }
    }

    @Cacheable(value = "shops", key = "#shopConfigRef.id", cacheManager = "shopsCacheManager")
    public ShopConfig getShop(ShopConfigRef shopConfigRef) {
        return getShop(shopConfigRef, VersionReference.head(new Head()));
    }

    private ShopConfig getShop(ShopConfigRef shopConfigRef, VersionReference versionReference)
            throws NotFoundException {
        log.debug("Trying to get shop from dominant, shopConfigRef='{}', versionReference='{}'", shopConfigRef,
                versionReference);
        try {
            var reference = new dev.vality.damsel.domain.Reference();
            reference.setShopConfig(shopConfigRef);
            VersionedObject versionedObject = checkoutObject(versionReference, reference);
            ShopConfig shop = versionedObject.getObject().getShopConfig().getData();
            log.debug("Shop has been found, shopConfigRef='{}', versionReference='{}'",
                    shopConfigRef, versionReference);
            return shop;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(
                    String.format("Version not found, proxyRef='%s', versionReference='%s'", shopConfigRef,
                            versionReference), ex, Type.PROXY);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get proxy, proxyRef='%s', versionReference='%s'",
                    shopConfigRef, versionReference), ex);
        }
    }

    private VersionedObject checkoutObject(VersionReference versionReference, Reference reference)
            throws TException {
        return dominantClient.checkoutObject(versionReference, reference);
    }
}
