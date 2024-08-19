package dev.vality.disputes.service;

import dev.vality.damsel.domain.*;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.damsel.proxy_provider.Cash;
import dev.vality.damsel.proxy_provider.Invoice;
import dev.vality.damsel.proxy_provider.*;
import dev.vality.disputes.service.external.DominantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentInfoExtractor {

    private final DominantService dominantService;

    @Async
    public CompletableFuture<PaymentInfo> getPaymentInfo(dev.vality.damsel.payment_processing.Invoice invoice,
                                                         InvoicePayment payment) {
        log.debug("Start extracting payment info");
        try {
            PaymentInfo paymentInfo = new PaymentInfo()
                    .setInvoice(createProxyProviderInvoice(invoice.getInvoice()))
                    .setPayment(createProxyProviderInvoicePayment(payment));
//                    .setShop(dawayDao.getShop(invoice.getInvoice().getOwnerId(), invoice.getInvoice().getShopId()));
            log.debug("Finish extracting payment info successfully");
            return CompletableFuture.completedFuture(paymentInfo);
        } catch (Exception e) {
            log.error("Finish extracting payment info with error", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private Invoice createProxyProviderInvoice(dev.vality.damsel.domain.Invoice domainInvoice) {
        Invoice invoice = new Invoice()
                .setId(domainInvoice.getId())
                .setCreatedAt(domainInvoice.getCreatedAt())
                .setDue(domainInvoice.getDue())
                .setDetails(domainInvoice.getDetails());

        var domainCost = domainInvoice.getCost();
        invoice.setCost(new Cash().setAmount(domainCost.getAmount())
                .setCurrency(dominantService.getCurrency(domainCost.getCurrency())));

        return invoice;
    }

    private dev.vality.damsel.proxy_provider.InvoicePayment createProxyProviderInvoicePayment(InvoicePayment payment) {
        var invoicePayment = new dev.vality.damsel.proxy_provider.InvoicePayment()
                .setId(payment.getPayment().getId())
                .setCreatedAt(payment.getPayment().getCreatedAt())
                .setTrx(payment.getLastTransactionInfo())
                .setProcessingDeadline(payment.getPayment().getProcessingDeadline())
                .setPayerSessionInfo(payment.getPayment().getPayerSessionInfo());

        enrichPaymentToolInfo(invoicePayment, payment);

        if (payment.getPayment().isSetCost()) {
            var cost = payment.getPayment().getCost();
            invoicePayment.setCost(new Cash().setAmount(cost.getAmount())
                    .setCurrency(dominantService.getCurrency(cost.getCurrency())));
        }

        return invoicePayment;
    }

    private Optional<PaymentServiceRef> getPaymentServiceRef(PaymentTool paymentTool) {
        return switch (paymentTool.getSetField()) {
            case GENERIC -> Optional.of(paymentTool.getGeneric().getPaymentService());
            case PAYMENT_TERMINAL -> Optional.of(paymentTool.getPaymentTerminal().getPaymentService());
            case DIGITAL_WALLET -> Optional.of(paymentTool.getDigitalWallet().getPaymentService());
            case BANK_CARD, MOBILE_COMMERCE, CRYPTO_CURRENCY -> Optional.empty();
        };
    }

    private void enrichPaymentToolInfo(dev.vality.damsel.proxy_provider.InvoicePayment proxyPayment,
                                       InvoicePayment processingPayment) {
        if (!processingPayment.getPayment().isSetPayer()) {
            return;
        }

        var payer = processingPayment.getPayment().getPayer();
        switch (payer.getSetField()) {
            case PAYMENT_RESOURCE -> enrichWithPaymentResourcePayer(proxyPayment, payer.getPaymentResource());
            case RECURRENT -> enrichWithRecurrentPayer(proxyPayment, payer.getRecurrent());
            case CUSTOMER -> {
                // Do nothing, because it's not used.
            }
            default -> throw new IllegalStateException(String.format("Unsupported payment type: %s",
                    payer.getSetField().getFieldName()));
        }
    }

    private void enrichWithPaymentResourcePayer(dev.vality.damsel.proxy_provider.InvoicePayment proxyPayment,
                                                PaymentResourcePayer paymentResourcePayer) {
        var paymentTool = paymentResourcePayer.getResource().getPaymentTool();
        proxyPayment.setPaymentService(getPaymentService(paymentTool));
        proxyPayment.setPaymentResource(
                PaymentResource.disposable_payment_resource(paymentResourcePayer.getResource()));
        proxyPayment.setContactInfo(paymentResourcePayer.getContactInfo());
    }

    private PaymentService getPaymentService(PaymentTool paymentTool) {
        Optional<PaymentServiceRef> paymentServiceRef = getPaymentServiceRef(paymentTool);
        return paymentServiceRef.map(dominantService::getPaymentService).orElse(null);
    }

    private void enrichWithRecurrentPayer(dev.vality.damsel.proxy_provider.InvoicePayment proxyPayment,
                                          RecurrentPayer recurrentPayer) {
        var paymentTool = recurrentPayer.getPaymentTool();
        proxyPayment.setPaymentService(getPaymentService(paymentTool));
        proxyPayment.setPaymentResource(
                PaymentResource.recurrent_payment_resource(
                        new RecurrentPaymentResource()
                                .setPaymentTool(paymentTool)));
        proxyPayment.setContactInfo(recurrentPayer.getContactInfo());
    }
}
