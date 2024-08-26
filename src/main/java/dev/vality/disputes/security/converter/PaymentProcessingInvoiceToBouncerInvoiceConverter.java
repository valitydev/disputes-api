package dev.vality.disputes.security.converter;

import dev.vality.bouncer.base.Entity;
import dev.vality.bouncer.context.v1.Payment;
import dev.vality.damsel.payment_processing.Invoice;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.damsel.payment_processing.InvoicePaymentRefund;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PaymentProcessingInvoiceToBouncerInvoiceConverter
        implements Converter<Invoice, dev.vality.bouncer.context.v1.Invoice> {

    @Override
    public dev.vality.bouncer.context.v1.Invoice convert(Invoice source) {
        var invoice = source.getInvoice();
        return new dev.vality.bouncer.context.v1.Invoice()
                .setId(source.getInvoice().getId())
                .setParty(new Entity().setId(invoice.getOwnerId()))
                .setShop(new Entity().setId(invoice.getShopId()))
                .setPayments(source.isSetPayments()
                        ? source.getPayments().stream().map(this::convertPayment).collect(Collectors.toSet())
                        : null);
    }

    private Payment convertPayment(InvoicePayment invoicePayment) {
        return new Payment().setId(invoicePayment.getPayment().getId())
                .setRefunds(invoicePayment.isSetRefunds()
                        ? invoicePayment.getRefunds().stream().map(this::convertRefund).collect(Collectors.toSet())
                        : null);
    }

    private Entity convertRefund(InvoicePaymentRefund invoiceRefund) {
        return new Entity().setId(invoiceRefund.getRefund().getId());
    }
}
