package dev.vality.disputes.api.service;

import dev.vality.damsel.domain.Currency;
import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.security.AccessData;
import dev.vality.disputes.service.external.impl.dominant.DominantAsyncService;
import dev.vality.disputes.service.external.impl.partymgnt.PartyManagementAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentParamsBuilder {

    private final DominantAsyncService dominantAsyncService;
    private final PartyManagementAsyncService partyManagementAsyncService;

    @SneakyThrows
    public PaymentParams buildGeneralPaymentContext(AccessData accessData) {
        var invoice = accessData.getInvoice().getInvoice();
        log.debug("Start building PaymentParams id={}", invoice.getId());
        var payment = accessData.getPayment();
        // http 500
        var terminal = dominantAsyncService.getTerminal(payment.getRoute().getTerminal());
        var currency = Optional.of(payment)
                .filter(p -> p.getPayment().isSetCost())
                .map(p -> p.getPayment().getCost())
                // http 500
                .map(cost -> dominantAsyncService.getCurrency(cost.getCurrency()));
        var shop = partyManagementAsyncService.getShop(invoice.getOwnerId(), invoice.getShopId());
        var paymentParams = PaymentParams.builder()
                .invoiceId(invoice.getId())
                .paymentId(payment.getPayment().getId())
                .terminalId(payment.getRoute().getTerminal().getId())
                .providerId(payment.getRoute().getProvider().getId())
                .providerTrxId(getProviderTrxId(payment))
                .currencyName(getCurrency(currency)
                        .map(Currency::getName).orElse(null))
                .currencySymbolicCode(getCurrency(currency)
                        .map(Currency::getSymbolicCode).orElse(null))
                .currencyNumericCode(getCurrency(currency)
                        .map(Currency::getNumericCode).map(Short::intValue).orElse(null))
                .currencyExponent(getCurrency(currency)
                        .map(Currency::getExponent).map(Short::intValue).orElse(null))
                .options(terminal.get().getOptions())
                .shopId(invoice.getShopId())
                .shopDetailsName(shop.get().getDetails().getName())
                .build();
        log.debug("Finish building PaymentParams {}", paymentParams);
        return paymentParams;
    }

    private Optional<Currency> getCurrency(Optional<CompletableFuture<Currency>> currency) {
        return currency.map(currencyCompletableFuture -> {
            try {
                return currencyCompletableFuture.get();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    private String getProviderTrxId(InvoicePayment payment) {
        return Optional.ofNullable(payment.getLastTransactionInfo())
                .map(TransactionInfo::getId)
                // http 500
                .orElseThrow();
    }
}
