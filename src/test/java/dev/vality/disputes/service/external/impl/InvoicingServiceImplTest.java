package dev.vality.disputes.service.external.impl;

import dev.vality.damsel.domain.RiskScore;
import dev.vality.damsel.payment_processing.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class InvoicingServiceImplTest {

    @Test
    void getInvoicePaymentRiskScoreShouldReturnLastPaymentRiskScore() throws Exception {
        var invoicingClient = mock(InvoicingSrv.Iface.class);
        when(invoicingClient.getEvents(eq("invoice_id"), any(EventRange.class)))
                .thenReturn(List.of(
                        createRiskScoreEvent(1L, "payment_id", RiskScore.low),
                        createRiskScoreEvent(2L, "other_payment_id", RiskScore.fatal),
                        createRiskScoreEvent(3L, "payment_id", RiskScore.high)
                ));

        var service = new InvoicingServiceImpl(invoicingClient);

        assertEquals("high", service.getInvoicePaymentRiskScore("invoice_id", "payment_id").orElseThrow());
    }

    @Test
    void getInvoicePaymentRiskScoreShouldReturnEmptyWhenRiskScoreNotFound() throws Exception {
        var invoicingClient = mock(InvoicingSrv.Iface.class);
        when(invoicingClient.getEvents(eq("invoice_id"), any(EventRange.class)))
                .thenReturn(List.of(createRiskScoreEvent(1L, "other_payment_id", RiskScore.low)));

        var service = new InvoicingServiceImpl(invoicingClient);

        assertTrue(service.getInvoicePaymentRiskScore("invoice_id", "payment_id").isEmpty());
    }

    private Event createRiskScoreEvent(long eventId, String paymentId, RiskScore riskScore) {
        return new Event()
                .setId(eventId)
                .setPayload(EventPayload.invoice_changes(List.of(
                        InvoiceChange.invoice_payment_change(new InvoicePaymentChange()
                                .setId(paymentId)
                                .setPayload(InvoicePaymentChangePayload.invoice_payment_risk_score_changed(
                                        new InvoicePaymentRiskScoreChanged(riskScore))))
                )));
    }
}
