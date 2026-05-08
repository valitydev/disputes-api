package dev.vality.disputes.service.external.impl;

import dev.vality.damsel.payment_processing.*;
import dev.vality.disputes.exception.InvoicePaymentAdjustmentPendingException;
import dev.vality.disputes.exception.InvoicingException;
import dev.vality.disputes.exception.InvoicingPaymentStatusRestrictionsException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.exception.NotFoundException.Type;
import dev.vality.disputes.service.external.InvoicingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoicingServiceImpl implements InvoicingService {

    private static final int EVENTS_PAGE_LIMIT = 100;

    private final InvoicingSrv.Iface invoicingClient;

    @Override
    public Invoice getInvoice(String invoiceId) {
        try {
            log.debug("Looking for invoice with id: {}", invoiceId);
            var invoice = Optional.ofNullable(invoicingClient.get(invoiceId, new EventRange()))
                    .orElseThrow(
                            () -> new NotFoundException(String.format("Unable to find invoice with id: %s", invoiceId),
                                    Type.INVOICE));
            log.debug("Found invoice with id: {}", invoiceId);
            return invoice;
        } catch (InvoiceNotFound ex) {
            throw new NotFoundException(String.format("Unable to find invoice with id: %s", invoiceId), ex,
                    Type.INVOICE);
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
                            String.format("Unable to find invoice with id: %s, paymentId: %s", invoiceId, paymentId),
                            Type.PAYMENT));
            log.debug("Found invoicePayment with id: {}", invoiceId);
            return invoicePayment;
        } catch (InvoiceNotFound ex) {
            throw new NotFoundException(String.format("Unable to find invoice with id: %s", invoiceId), ex,
                    Type.INVOICE);
        } catch (InvoicePaymentNotFound ex) {
            throw new NotFoundException(
                    String.format("Unable to find invoice with id: %s, paymentId: %s", invoiceId, paymentId), ex,
                    Type.PAYMENT);
        } catch (TException ex) {
            throw new InvoicingException(
                    String.format("Failed to get invoicePayment with id: %s, paymentId: %s", invoiceId, paymentId), ex);
        }
    }

    @Override
    public Optional<String> getInvoicePaymentRiskScore(String invoiceId, String paymentId) {
        try {
            log.debug("Looking for invoicePayment riskScore, invoiceId={}, paymentId={}", invoiceId, paymentId);
            Long after = null;
            String riskScore = null;
            var hasMoreEvents = true;
            while (hasMoreEvents) {
                var range = new EventRange().setLimit(EVENTS_PAGE_LIMIT);
                if (after != null) {
                    range.setAfter(after);
                }
                var events = Optional.ofNullable(invoicingClient.getEvents(invoiceId, range)).orElse(List.of());
                for (var event : events) {
                    after = event.getId();
                    if (event.isSetPayload() && event.getPayload().isSetInvoiceChanges()) {
                        riskScore = getRiskScore(paymentId, event).orElse(riskScore);
                    }
                }
                hasMoreEvents = events.size() == EVENTS_PAGE_LIMIT;
            }
            log.debug("InvoicePayment riskScore has been found, invoiceId={}, paymentId={}, riskScore={}",
                    invoiceId, paymentId, riskScore);
            return Optional.ofNullable(riskScore);
        } catch (InvoiceNotFound ex) {
            throw new NotFoundException(String.format("Unable to find invoice with id: %s", invoiceId), ex,
                    Type.INVOICE);
        } catch (EventNotFound ex) {
            throw new InvoicingException(
                    String.format("Failed to get invoice events with id: %s, event not found", invoiceId), ex);
        } catch (TException ex) {
            throw new InvoicingException(
                    String.format("Failed to get invoicePayment riskScore with id: %s, paymentId: %s", invoiceId,
                            paymentId), ex);
        }
    }

    private Optional<String> getRiskScore(String paymentId, Event event) {
        return event.getPayload().getInvoiceChanges().stream()
                .filter(invoiceChange -> invoiceChange.isSetInvoicePaymentChange()
                        && paymentId.equals(invoiceChange.getInvoicePaymentChange().getId()))
                .map(invoiceChange -> invoiceChange.getInvoicePaymentChange().getPayload())
                .filter(InvoicePaymentChangePayload::isSetInvoicePaymentRiskScoreChanged)
                .map(payload -> payload.getInvoicePaymentRiskScoreChanged().getRiskScore().name())
                .reduce((previous, current) -> current);
    }

    @Override
    public void createPaymentAdjustment(
            String invoiceId,
            String paymentId,
            InvoicePaymentAdjustmentParams params) {
        try {
            log.debug("createPaymentAdjustment with id: {}", invoiceId);
            invoicingClient.createPaymentAdjustment(invoiceId, paymentId, params);
            log.debug("Done createPaymentAdjustment with id: {}", invoiceId);
        } catch (InvoiceNotFound ex) {
            throw new NotFoundException(String.format("Unable to find invoice with id: %s", invoiceId), ex,
                    Type.INVOICE);
        } catch (InvoicePaymentNotFound ex) {
            throw new NotFoundException(
                    String.format("Unable to find invoice with id: %s, paymentId: %s", invoiceId, paymentId), ex,
                    Type.PAYMENT);
        } catch (InvoicePaymentAdjustmentPending ex) {
            throw new InvoicePaymentAdjustmentPendingException();
        } catch (InvalidPaymentStatus | InvalidPaymentTargetStatus ex) {
            throw new InvoicingPaymentStatusRestrictionsException(ex, null);
        } catch (TException ex) {
            throw new InvoicingException(String.format("Failed to createPaymentAdjustment with id: %s", invoiceId), ex);
        }
    }
}
