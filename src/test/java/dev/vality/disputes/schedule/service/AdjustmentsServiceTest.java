package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.schedule.converter.InvoicePaymentCapturedAdjustmentParamsConverter;
import dev.vality.disputes.schedule.core.AdjustmentsService;
import dev.vality.disputes.schedule.service.config.DisputeApiTestService;
import dev.vality.disputes.schedule.service.config.PendingDisputesTestService;
import dev.vality.disputes.util.MockUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.getCapturedInvoicePaymentAdjustment;
import static dev.vality.disputes.util.MockUtil.getCashFlowInvoicePaymentAdjustment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
@Import({PendingDisputesTestService.class})
@SuppressWarnings({"ParameterName", "LineLength"})
public class AdjustmentsServiceTest {

    @Autowired
    private InvoicingSrv.Iface invoicingClient;
    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private AdjustmentsService adjustmentsService;
    @Autowired
    private DisputeApiTestService disputeApiTestService;
    @Autowired
    private PendingDisputesTestService pendingDisputesTestService;
    @Autowired
    private InvoicePaymentCapturedAdjustmentParamsConverter invoicePaymentCapturedAdjustmentParamsConverter;
    @Autowired
    private AdjustmentExtractor adjustmentExtractor;

    @Test
    @SneakyThrows
    public void testPaymentNotFound() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        disputeDao.update(disputeId, DisputeStatus.create_adjustment);
        var dispute = disputeDao.get(disputeId);
        adjustmentsService.callHgForCreateAdjustment(dispute.get());
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).get().getStatus());
        assertEquals(ErrorReason.PAYMENT_NOT_FOUND, disputeDao.get(disputeId).get().getErrorMessage());
    }

    @Test
    @SneakyThrows
    public void testInvoiceNotFound() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        var dispute = disputeDao.get(disputeId);
        adjustmentsService.callHgForCreateAdjustment(dispute.get());
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).get().getStatus());
        assertEquals(ErrorReason.INVOICE_NOT_FOUND, disputeDao.get(disputeId).get().getErrorMessage());
    }

    @Test
    @SneakyThrows
    public void testFullSuccessFlow() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        var dispute = disputeDao.get(disputeId);
        dispute.get().setReason("test adj");
        dispute.get().setChangedAmount(dispute.get().getAmount() + 1);
        var adjustmentId = "adjustmentId";
        var reason = adjustmentExtractor.getReason(dispute.get());
        when(invoicingClient.createPaymentAdjustment(any(), any(), any()))
                .thenReturn(getCapturedInvoicePaymentAdjustment(adjustmentId, reason));
        adjustmentsService.callHgForCreateAdjustment(dispute.get());
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).get().getStatus());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }

    @Test
    @SneakyThrows
    public void testDisputesAdjustmentExist() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        var dispute = disputeDao.get(disputeId);
        dispute.get().setReason("test adj");
        dispute.get().setChangedAmount(dispute.get().getAmount() + 1);
        var adjustmentId = "adjustmentId";
        var invoicePayment = MockUtil.createInvoicePayment(dispute.get().getPaymentId());
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        invoicePayment.setAdjustments(List.of(
                getCapturedInvoicePaymentAdjustment(adjustmentId, adjustmentExtractor.getReason(dispute.get())),
                getCashFlowInvoicePaymentAdjustment(adjustmentId, adjustmentExtractor.getReason(dispute.get()))));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        adjustmentsService.callHgForCreateAdjustment(dispute.get());
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).get().getStatus());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }
}
