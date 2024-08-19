package dev.vality.disputes.service;

import dev.vality.disputes.model.PaymentParams;
import dev.vality.disputes.security.AccessData;
import dev.vality.disputes.service.external.DominantService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentParamsBuilder {

    private final DominantService dominantService;

    @SneakyThrows
    public PaymentParams buildGeneralPaymentContext(AccessData accessData) {
        log.debug("Start building ContextPaymentDto");
        var invoice = accessData.getInvoice();
        var payment = accessData.getPayment();
        var terminal = dominantService.getTerminal(payment.getRoute().getTerminal());
        log.debug("Finish building ContextPaymentDto");
        return PaymentParams.builder()
                .invoiceId(invoice.getInvoice().getId())
                .paymentId(payment.getPayment().getId())
                .options(terminal.get().getOptions())
                .terminalId(String.valueOf(payment.getRoute().getTerminal().getId()))
                .providerId(String.valueOf(payment.getRoute().getProvider().getId()))
                .build();
    }
}
