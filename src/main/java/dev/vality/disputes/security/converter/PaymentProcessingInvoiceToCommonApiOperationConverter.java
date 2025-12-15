package dev.vality.disputes.security.converter;

import dev.vality.bouncer.base.Entity;
import dev.vality.bouncer.context.v1.CommonAPIOperation;
import dev.vality.damsel.payment_processing.Invoice;
import dev.vality.disputes.config.properties.BouncerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentProcessingInvoiceToCommonApiOperationConverter
        implements Converter<Invoice, dev.vality.bouncer.context.v1.ContextCommonAPI> {

    private final BouncerProperties bouncerProperties;

    @Override
    public dev.vality.bouncer.context.v1.ContextCommonAPI convert(Invoice source) {
        var invoice = source.getInvoice();
        return new dev.vality.bouncer.context.v1.ContextCommonAPI()
                .setOp(new CommonAPIOperation()
                        .setId(bouncerProperties.getOperationId())
                        .setInvoice(new Entity().setId(source.getInvoice().getId()))
                        .setParty(new Entity().setId(invoice.getPartyRef().getId()))
                        .setShop(new Entity().setId(invoice.getShopRef().getId())));
    }
}
