package dev.vality.disputes.service.external.impl;

import dev.vality.damsel.domain.InvoicePaymentAdjustment;
import dev.vality.damsel.payment_processing.*;
import dev.vality.disputes.exception.InvoicingException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.service.external.InvoicingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoicingServiceImpl implements InvoicingService {

    private final InvoicingSrv.Iface invoicingClient;

    @Override
    public Invoice getInvoice(String invoiceId) {
        try {
            log.debug("Looking for invoice with id: {}", invoiceId);
            Invoice invoice = invoicingClient.get(invoiceId, new EventRange());
            log.debug("Found invoice with id: {}", invoiceId);
            return invoice;
        } catch (InvoiceNotFound e) {
            throw new NotFoundException(String.format("Unable to find invoice with id: %s", invoiceId), e);
        } catch (TException e2) {
            throw new InvoicingException(String.format("Failed to get invoice with id: %s", invoiceId), e2);
        }
    }

    @Override
    public InvoicePaymentAdjustment createPaymentAdjustment(
            String invoiceId,
            String paymentId,
            InvoicePaymentAdjustmentParams params) {
        try {
            log.debug("createPaymentAdjustment with id: {}", invoiceId);
            var invoice = invoicingClient.createPaymentAdjustment(invoiceId, paymentId, params);
            log.debug("Done createPaymentAdjustment with id: {}", invoiceId);
            return invoice;
        } catch (InvoiceNotFound e) {
            // закрываем диспут с фейлом если получили не преодолимый отказ внешних шлюзов с ключевыми данными
            return null;
        } catch (TException e2) {
            throw new InvoicingException(String.format("Failed to createPaymentAdjustment with id: %s", invoiceId), e2);
        }
    }
}
