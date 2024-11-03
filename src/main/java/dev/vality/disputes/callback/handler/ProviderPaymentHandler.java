package dev.vality.disputes.callback.handler;

import dev.vality.disputes.callback.service.ProviderPaymentsService;
import dev.vality.disputes.domain.tables.pojos.ProviderCallback;
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
        currentThread.setName("provider-payments-id-" + providerCallback.getInvoiceId() +
                "-" + providerCallback.getPaymentId() + "-" + oldName);
        try {
            providerPaymentsService.callHgForCreateAdjustment(providerCallback);
            return providerCallback.getId();
        } catch (Throwable ex) {
            log.error("Received exception while scheduler processed ProviderPayments callHgForCreateAdjustment", ex);
            throw ex;
        } finally {
            currentThread.setName(oldName);
        }
    }
}
