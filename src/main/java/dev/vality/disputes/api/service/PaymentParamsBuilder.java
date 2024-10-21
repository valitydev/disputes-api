package dev.vality.disputes.api.service;

import dev.vality.damsel.domain.Cash;
import dev.vality.damsel.domain.CurrencyRef;
import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.security.AccessData;
import dev.vality.disputes.service.external.impl.partymgnt.PartyManagementAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentParamsBuilder {

    private final ProviderDataService providerDataService;
    private final PartyManagementAsyncService partyManagementAsyncService;

    @SneakyThrows
    public PaymentParams buildGeneralPaymentContext(AccessData accessData) {
        var invoice = accessData.getInvoice().getInvoice();
        log.debug("Start building PaymentParams id={}", invoice.getId());
        var payment = accessData.getPayment();
        // http 500
        var currency = providerDataService.getCurrency(getCurrencyRef(payment));
        var shop = partyManagementAsyncService.getShop(invoice.getOwnerId(), invoice.getShopId());
        var paymentParams = PaymentParams.builder()
                .invoiceId(invoice.getId())
                .paymentId(payment.getPayment().getId())
                .terminalId(payment.getRoute().getTerminal().getId())
                .providerId(payment.getRoute().getProvider().getId())
                .providerTrxId(getProviderTrxId(payment))
                .currencyName(currency.getName())
                .currencySymbolicCode(currency.getSymbolicCode())
                .currencyNumericCode((int) currency.getNumericCode())
                .currencyExponent((int) currency.getExponent())
                // http 500
                .options(providerDataService.getProviderData(payment).getOptions())
                .shopId(invoice.getShopId())
                .shopDetailsName(shop.get().getDetails().getName())
                .invoiceAmount(payment.getPayment().getCost().getAmount())
                .build();
        log.debug("Finish building PaymentParams {}", paymentParams);
        return paymentParams;
    }

    private CurrencyRef getCurrencyRef(InvoicePayment payment) {
        return Optional.of(payment)
                .filter(p -> p.getPayment().isSetCost())
                .map(p -> p.getPayment().getCost())
                .map(Cash::getCurrency)
                .orElse(null);
    }

    private String getProviderTrxId(InvoicePayment payment) {
        return Optional.ofNullable(payment.getLastTransactionInfo())
                .map(TransactionInfo::getId)
                // http 500
                .orElseThrow();
    }
}
