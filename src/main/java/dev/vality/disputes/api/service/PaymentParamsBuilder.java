package dev.vality.disputes.api.service;

import dev.vality.damsel.domain.TransactionInfo;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.exception.ProviderTrxIdNotFoundException;
import dev.vality.disputes.schedule.service.ProviderDataService;
import dev.vality.disputes.security.AccessData;
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

    @SneakyThrows
    public PaymentParams buildGeneralPaymentContext(AccessData accessData) {
        var invoice = accessData.getInvoice().getInvoice();
        log.debug("Start building PaymentParams id={}", invoice.getId());
        var payment = accessData.getPayment();
        var currency = providerDataService.getAsyncCurrency(payment);
        var shop = providerDataService.getAsyncShop(accessData.getInvoice());
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
                .options(providerDataService.getAsyncProviderData(payment).getOptions())
                .shopId(invoice.getShopId())
                .shopDetailsName(shop.getName())
                .invoiceAmount(payment.getPayment().getCost().getAmount())
                .build();
        log.debug("Finish building PaymentParams {}", paymentParams);
        return paymentParams;
    }

    private String getProviderTrxId(InvoicePayment payment) {
        return Optional.ofNullable(payment.getLastTransactionInfo())
                .map(TransactionInfo::getId)
                .orElseThrow(() -> new ProviderTrxIdNotFoundException(
                        String.format("Payment with id: %s and filled ProviderTrxId not found!",
                                payment.getPayment().getId())));
    }
}
