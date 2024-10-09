package dev.vality.disputes.schedule.service;

import dev.vality.damsel.domain.InvoicePaymentCaptured;
import dev.vality.damsel.domain.InvoicePaymentStatus;
import dev.vality.damsel.payment_processing.InvoicingSrv;
import dev.vality.disputes.config.WireMockSpringBootITest;
import dev.vality.disputes.constant.ErrorReason;
import dev.vality.disputes.dao.DisputeDao;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.schedule.converter.InvoicePaymentAdjustmentParamsConverter;
import dev.vality.disputes.schedule.service.config.DisputeApiTestService;
import dev.vality.disputes.schedule.service.config.PendingDisputesTestService;
import dev.vality.disputes.util.MockUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static dev.vality.disputes.util.MockUtil.getInvoicePaymentAdjustment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WireMockSpringBootITest
@Import({PendingDisputesTestService.class})
@SuppressWarnings({"ParameterName", "LineLength"})
public class CreateAdjustmentsServiceTest {

    @Autowired
    private InvoicingSrv.Iface invoicingClient;
    @Autowired
    private DisputeDao disputeDao;
    @Autowired
    private CreateAdjustmentsService createAdjustmentsService;
    @Autowired
    private DisputeApiTestService disputeApiTestService;
    @Autowired
    private PendingDisputesTestService pendingDisputesTestService;
    @Autowired
    private InvoicePaymentAdjustmentParamsConverter invoicePaymentAdjustmentParamsConverter;

    @Test
    @SneakyThrows
    public void testPaymentNotFound() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        disputeDao.update(disputeId, DisputeStatus.create_adjustment);
        var dispute = disputeDao.get(disputeId);
        createAdjustmentsService.callHgForCreateAdjustment(dispute.get());
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).get().getStatus());
        assertEquals(ErrorReason.PAYMENT_NOT_FOUND, disputeDao.get(disputeId).get().getErrorMessage());
    }

    @Test
    @SneakyThrows
    public void testDisputesAdjustmentExist() {
        var invoiceId = "20McecNnWoy";
        var paymentId = "1";
        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
        disputeDao.update(disputeId, DisputeStatus.create_adjustment);
        var invoicePayment = MockUtil.createInvoicePayment(paymentId);
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        var dispute = disputeDao.get(disputeId);
        dispute.get().setReason("test adj");
        var adjustmentId = "adjustmentId";
        var invoicePaymentAdjustment = getInvoicePaymentAdjustment(adjustmentId, invoicePaymentAdjustmentParamsConverter.getReason(dispute.get()));
        invoicePayment.setAdjustments(List.of(invoicePaymentAdjustment));
        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
        createAdjustmentsService.callHgForCreateAdjustment(dispute.get());
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).get().getStatus());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }

    @Test
    @SneakyThrows
    public void testInvoiceNotFound() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        var paymentId = "1";
        var invoicePayment = MockUtil.createInvoicePayment(paymentId);
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        var dispute = disputeDao.get(disputeId);
        createAdjustmentsService.callHgForCreateAdjustment(dispute.get());
        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).get().getStatus());
        assertEquals(ErrorReason.INVOICE_NOT_FOUND, disputeDao.get(disputeId).get().getErrorMessage());
    }

    @Test
    @SneakyThrows
    public void testFullSuccessFlow() {
        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
        var paymentId = "1";
        var invoicePayment = MockUtil.createInvoicePayment(paymentId);
        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
        var dispute = disputeDao.get(disputeId);
        dispute.get().setReason("test adj");
        var adjustmentId = "adjustmentId";
        var reason = invoicePaymentAdjustmentParamsConverter.getReason(dispute.get());
        when(invoicingClient.createPaymentAdjustment(any(), any(), any()))
                .thenReturn(getInvoicePaymentAdjustment(adjustmentId, reason));
        createAdjustmentsService.callHgForCreateAdjustment(dispute.get());
        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).get().getStatus());
        disputeDao.update(disputeId, DisputeStatus.failed);
    }
}
