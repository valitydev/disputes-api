package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.Currency;
import dev.vality.damsel.domain.CurrencyRef;
import dev.vality.damsel.domain.ProviderRef;
import dev.vality.damsel.domain.TerminalRef;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.schedule.model.ProviderData;
import dev.vality.disputes.service.external.DominantService;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.disputes.utils.OptionsExtractors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderDataService {

    private final DominantService dominantService;
    private final DominantAsyncService dominantAsyncService;

    public ProviderData getProviderData(Integer providerId, Integer terminalId) {
        var provider = dominantService.getProvider(new ProviderRef(providerId));
        var terminal = dominantService.getTerminal(new TerminalRef(terminalId));
        var proxy = dominantService.getProxy(provider.getProxy().getRef());
        return ProviderData.builder()
                .options(OptionsExtractors.mergeOptions(provider, proxy, terminal))
                .defaultProviderUrl(proxy.getUrl())
                .build();
    }

    @SneakyThrows
    public ProviderData getProviderData(InvoicePayment payment) {
        var provider = dominantAsyncService.getProvider(payment.getRoute().getProvider());
        var terminal = dominantAsyncService.getTerminal(payment.getRoute().getTerminal());
        var proxy = dominantAsyncService.getProxy(provider.get().getProxy().getRef());
        return ProviderData.builder()
                .options(OptionsExtractors.mergeOptions(provider, proxy, terminal))
                .defaultProviderUrl(proxy.get().getUrl())
                .build();
    }

    @SneakyThrows
    public Currency getCurrency(CurrencyRef currencyRef) {
        return currencyRef == null ? new Currency() : dominantAsyncService.getCurrency(currencyRef).get();
    }
}
