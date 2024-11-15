package dev.vality.disputes.provider.payments.handler;

import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class ProviderPaymentHandler {

    private final ProviderPaymentsService providerPaymentsService;

    public UUID handle(ProviderCallback providerCallback) {
        final var currentThread = Thread.currentThread();
        final var oldName = currentThread.getName();
        currentThread.setName("provider-payments-" + providerCallback.getInvoiceId() +
                "." + providerCallback.getPaymentId() + "-" + oldName);
        try {
            providerPaymentsService.callHgForCreateAdjustment(providerCallback);
            return providerCallback.getId();
        } catch (Throwable ex) {
            log.warn("Received exception while scheduler processed ProviderPayments callHgForCreateAdjustment", ex);
            throw ex;
        } finally {
            currentThread.setName(oldName);
        }
    }
}
