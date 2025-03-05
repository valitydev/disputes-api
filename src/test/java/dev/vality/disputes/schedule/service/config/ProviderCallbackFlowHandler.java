package dev.vality.disputes.schedule.service.config;

import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.domain.enums.ProviderPaymentsStatus;
import dev.vality.disputes.provider.payments.dao.ProviderCallbackDao;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsAdjustmentExtractor;
import dev.vality.disputes.provider.payments.service.ProviderPaymentsService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.util.List;
import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RequiredArgsConstructor
@SuppressWarnings({"LineLength", "VariableDeclarationUsageDistance"})
public class ProviderCallbackFlowHandler {

    private final InvoicingSrv.Iface invoicingClient;
    private final DisputeDao disputeDao;
    private final ProviderCallbackDao providerCallbackDao;
    private final PendingFlowHandler pendingFlowHandler;
    private final ProviderPaymentsService providerPaymentsService;
    private final ProviderPaymentsAdjustmentExtractor providerPaymentsAdjustmentExtractor;

    @SneakyThrows
    public UUID handleSuccess() {
        var disputeId = pendingFlowHandler.handlePending();
        var dispute = disputeDao.get(disputeId);
        var providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        var reason = providerPaymentsAdjustmentExtractor.getReason(providerCallback);
        var invoicePayment = createInvoicePayment(providerCallback.getPaymentId());
        invoicePayment.setAdjustments(List.of(getCashFlowInvoicePaymentAdjustment("adjustmentId", reason)));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        when(invoicingClient.createPaymentAdjustment(any(), any(), any()))
                .thenReturn(getCapturedInvoicePaymentAdjustment("adjustmentId", reason));
        providerPaymentsService.callHgForCreateAdjustment(providerCallback);
        providerCallback = providerCallbackDao.get(dispute.getInvoiceId(), dispute.getPaymentId());
        assertEquals(ProviderPaymentsStatus.succeeded, providerCallback.getStatus());
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
        return disputeId;
    }
}
