package dev.vality.disputes.service.external.impl.dominant;

import dev.vality.damsel.domain.*;
import dev.vality.damsel.domain_config.Reference;
import dev.vality.damsel.domain_config.*;
import dev.vality.disputes.exception.DominantException;
import dev.vality.disputes.exception.NotFoundException;
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
    public Currency getCurrency(CurrencyRef currencyRef) throws NotFoundException {
        return getCurrency(currencyRef, Reference.head(new Head()));
    }

    private Currency getCurrency(CurrencyRef currencyRef, Reference revisionReference)
            throws NotFoundException {
        log.debug("Trying to get currency, currencyRef='{}', revisionReference='{}'", currencyRef, revisionReference);
        try {
            var reference = new dev.vality.damsel.domain.Reference();
            reference.setCurrency(currencyRef);
            VersionedObject versionedObject = checkoutObject(revisionReference, reference);
            Currency currency = versionedObject.getObject().getCurrency().getData();
            log.debug("Currency has been found, currencyRef='{}', revisionReference='{}', currency='{}'",
                    currencyRef, revisionReference, currency);
            return currency;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(String.format("Version not found, currencyRef='%s', revisionReference='%s'",
                    currencyRef, revisionReference), ex);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get currency, currencyRef='%s', " +
                    "revisionReference='%s'", currencyRef, revisionReference), ex);
        }
    }

    @Cacheable(value = "terminals", key = "#terminalRef.id", cacheManager = "terminalsCacheManager")
    public Terminal getTerminal(TerminalRef terminalRef) {
        return getTerminal(terminalRef, Reference.head(new Head()));
    }

    public Terminal getTerminal(TerminalRef terminalRef, Reference revisionReference)
            throws NotFoundException {
        log.debug("Trying to get terminal from dominant, terminalRef='{}', revisionReference='{}'", terminalRef,
                revisionReference);
        try {
            var reference = new dev.vality.damsel.domain.Reference();
            reference.setTerminal(terminalRef);
            VersionedObject versionedObject = checkoutObject(revisionReference, reference);
            Terminal terminal = versionedObject.getObject().getTerminal().getData();
            log.debug("Terminal has been found, terminalRef='{}', revisionReference='{}', terminal='{}'",
                    terminalRef, revisionReference, terminal);
            return terminal;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(String.format("Version not found, terminalRef='%s', revisionReference='%s'",
                    terminalRef, revisionReference), ex);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get terminal, terminalRef='%s'," +
                    " revisionReference='%s'", terminalRef, revisionReference), ex);
        }
    }

    @Cacheable(value = "providers", key = "#providerRef.id", cacheManager = "providersCacheManager")
    public Provider getProvider(ProviderRef providerRef) {
        return getProvider(providerRef, Reference.head(new Head()));
    }

    private Provider getProvider(ProviderRef providerRef, Reference revisionReference)
            throws NotFoundException {
        log.debug("Trying to get provider from dominant, providerRef='{}', revisionReference='{}'", providerRef,
                revisionReference);
        try {
            var reference = new dev.vality.damsel.domain.Reference();
            reference.setProvider(providerRef);
            VersionedObject versionedObject = checkoutObject(revisionReference, reference);
            Provider provider = versionedObject.getObject().getProvider().getData();
            log.debug("Provider has been found, providerRef='{}', revisionReference='{}', terminal='{}'",
                    providerRef, revisionReference, provider);
            return provider;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(String.format("Version not found, providerRef='%s', revisionReference='%s'",
                    providerRef, revisionReference), ex);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get provider, providerRef='%s'," +
                    " revisionReference='%s'", providerRef, revisionReference), ex);
        }
    }

    @Cacheable(value = "payment_services", key = "#paymentServiceRef.id", cacheManager = "paymentServicesCacheManager")
    public PaymentService getPaymentService(PaymentServiceRef paymentServiceRef) {
        return getPaymentService(paymentServiceRef, Reference.head(new Head()));
    }

    private PaymentService getPaymentService(PaymentServiceRef paymentServiceRef, Reference revisionReference)
            throws NotFoundException {
        log.debug("Trying to get paymentService from dominant, paymentServiceRef='{}', revisionReference='{}'",
                paymentServiceRef,
                revisionReference);
        try {
            var reference = new dev.vality.damsel.domain.Reference();
            reference.setPaymentService(paymentServiceRef);
            VersionedObject versionedObject = checkoutObject(revisionReference, reference);
            PaymentService paymentService = versionedObject.getObject().getPaymentService().getData();
            log.debug("PaymentService has been found, paymentServiceRef='{}', revisionReference='{}', terminal='{}'",
                    paymentServiceRef, revisionReference, paymentService);
            return paymentService;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(String.format("Version not found, paymentServiceRef='%s', " +
                            "revisionReference='%s'",
                    paymentServiceRef, revisionReference), ex);
        } catch (TException ex) {
            throw new RuntimeException(String.format("Failed to get paymentService, paymentServiceRef='%s', " +
                            "revisionReference='%s'",
                    paymentServiceRef, revisionReference), ex);
        }
    }

    @Cacheable(value = "proxies", key = "#proxyRef.id", cacheManager = "proxiesCacheManager")
    public ProxyDefinition getProxy(ProxyRef proxyRef) {
        return getProxy(proxyRef, Reference.head(new Head()));
    }


    private ProxyDefinition getProxy(ProxyRef proxyRef, Reference revisionReference)
            throws NotFoundException {
        log.debug("Trying to get proxy from dominant, proxyRef='{}', revisionReference='{}'", proxyRef,
                revisionReference);
        try {
            var reference = new dev.vality.damsel.domain.Reference();
            reference.setProxy(proxyRef);
            VersionedObject versionedObject = checkoutObject(revisionReference, reference);
            ProxyDefinition proxy = versionedObject.getObject().getProxy().getData();
            log.debug("Proxy has been found, proxyRef='{}', revisionReference='{}'", proxyRef, revisionReference);
            return proxy;
        } catch (VersionNotFound | ObjectNotFound ex) {
            throw new NotFoundException(String.format("Version not found, proxyRef='%s', revisionReference='%s'",
                    proxyRef, revisionReference), ex);
        } catch (TException ex) {
            throw new DominantException(String.format("Failed to get proxy, proxyRef='%s', revisionReference='%s'",
                    proxyRef, revisionReference), ex);
        }
    }

    private VersionedObject checkoutObject(Reference revisionReference, dev.vality.damsel.domain.Reference reference)
            throws TException {
        return dominantClient.checkoutObject(revisionReference, reference);
    }

}
