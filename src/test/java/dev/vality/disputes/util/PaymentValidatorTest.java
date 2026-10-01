package dev.vality.disputes.util;

import dev.vality.damsel.domain.Invoice;
import dev.vality.damsel.domain.InvoicePayment;
import dev.vality.disputes.exception.PaymentExpiredException;
import dev.vality.disputes.security.AccessData;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentValidatorTest {

    @Test
    void usesConfiguredMaxPaymentAgeDays() {
        var payment = new InvoicePayment()
                .setCreatedAt(LocalDateTime.now().minusDays(40).toInstant(ZoneOffset.UTC).toString());
        var accessData = AccessData.builder()
                .invoice(new dev.vality.damsel.payment_processing.Invoice()
                        .setInvoice(new Invoice().setId("invoice-id")))
                .payment(new dev.vality.damsel.payment_processing.InvoicePayment().setPayment(payment))
                .build();

        assertThrows(PaymentExpiredException.class, () -> PaymentValidator.validatePaymentAge(accessData, 30));
        assertDoesNotThrow(() -> PaymentValidator.validatePaymentAge(accessData, 60));
    }
}
