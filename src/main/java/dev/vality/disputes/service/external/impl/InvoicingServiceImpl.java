package dev.vality.disputes.service.external.impl;

import dev.vality.damsel.domain.InvoicePaymentAdjustment;
import dev.vality.damsel.payment_processing.*;
import dev.vality.disputes.exception.InvoicingException;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.exception.NotFoundException.Type;
import dev.vality.disputes.service.external.InvoicingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"LineLength"})
public class InvoicingServiceImpl implements InvoicingService {

    private final InvoicingSrv.Iface invoicingClient;

    @Override
    public Invoice getInvoice(String invoiceId) {
        try {
            log.debug("Looking for invoice with id: {}", invoiceId);
            var invoice = Optional.ofNullable(invoicingClient.get(invoiceId, new EventRange()))
                    .orElseThrow(() -> new NotFoundException(
                            String.format("Invoice not found, id: %s", invoiceId), Type.INVOICE));
            log.debug("Found invoice with id: {}", invoiceId);
            return invoice;
        } catch (InvoiceNotFound ex) {
            throw new NotFoundException(String.format("Unable to find invoice with id: %s", invoiceId), ex, Type.INVOICE);
        } catch (TException ex) {
            throw new InvoicingException(String.format("Failed to get invoice with id: %s", invoiceId), ex);
        }
    }

    @Override
    public InvoicePayment getInvoicePayment(String invoiceId, String paymentId) {
        try {
            log.debug("Looking for invoicePayment with id: {}", invoiceId);
            var invoicePayment = Optional.ofNullable(invoicingClient.getPayment(invoiceId, paymentId))
                    .orElseThrow(() -> new NotFoundException(
                            String.format("InvoicePayment not found, id: %s, paymentId: %s", invoiceId, paymentId), Type.PAYMENT));
            log.debug("Found invoicePayment with id: {}", invoiceId);
            return invoicePayment;
        } catch (InvoiceNotFound ex) {
            throw new NotFoundException(String.format("Unable to find invoice with id: %s", invoiceId), ex, Type.INVOICE);
        } catch (InvoicePaymentNotFound ex) {
            throw new NotFoundException(String.format("Unable to find invoice with id: %s, paymentId: %s", invoiceId, paymentId), ex, Type.PAYMENT);
        } catch (TException ex) {
            throw new InvoicingException(String.format("Failed to get invoicePayment with id: %s, paymentId: %s", invoiceId, paymentId), ex);
        }
    }

    @Override
    public InvoicePaymentAdjustment createPaymentAdjustment(
            String invoiceId,
            String paymentId,
            InvoicePaymentAdjustmentParams params) {
        try {
            log.debug("createPaymentAdjustment with id: {}", invoiceId);
            var invoicePaymentAdjustment = invoicingClient.createPaymentAdjustment(invoiceId, paymentId, params);
            log.debug("Done createPaymentAdjustment with id: {}", invoiceId);
            return invoicePaymentAdjustment;
        } catch (InvoiceNotFound ex) {
            throw new NotFoundException(String.format("Unable to find invoice with id: %s", invoiceId), ex, Type.INVOICE);
        } catch (InvoicePaymentNotFound ex) {
            throw new NotFoundException(String.format("Unable to find invoice with id: %s, paymentId: %s", invoiceId, paymentId), ex, Type.PAYMENT);
        } catch (InvalidPaymentStatus | InvalidPaymentTargetStatus ex) {
            throw new InvoicingPaymentStatusRestrictionsException(ex);
        } catch (TException ex) {
            throw new InvoicingException(String.format("Failed to createPaymentAdjustment with id: %s", invoiceId), ex);
        }
    }
}
