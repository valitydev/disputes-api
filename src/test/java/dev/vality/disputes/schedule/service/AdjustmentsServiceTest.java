package dev.vality.disputes.schedule.service;

//@WireMockSpringBootITest
//@Import({PendingDisputesTestService.class})
@SuppressWarnings({"LineLength"})
public class AdjustmentsServiceTest {
//
//    @Autowired
//    private InvoicingSrv.Iface invoicingClient;
//    @Autowired
//    private DisputeDao disputeDao;
//    @Autowired
//    private AdjustmentsService adjustmentsService;
//    @Autowired
//    private DisputeApiTestService disputeApiTestService;
//    @Autowired
//    private PendingDisputesTestService pendingDisputesTestService;
//    @Autowired
//    private InvoicePaymentCapturedAdjustmentParamsConverter invoicePaymentCapturedAdjustmentParamsConverter;
//    @Autowired
//    private AdjustmentExtractor adjustmentExtractor;
//
//    @Test
//    @SneakyThrows
//    public void testPaymentNotFound() {
//        var invoiceId = "20McecNnWoy";
//        var paymentId = "1";
//        var disputeId = UUID.fromString(disputeApiTestService.createDisputeViaApi(invoiceId, paymentId).getDisputeId());
//        disputeDao.update(disputeId, DisputeStatus.create_adjustment);
//        var dispute = disputeDao.get(disputeId);
//        adjustmentsService.callHgForCreateAdjustment(dispute);
//        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
//        assertEquals(ErrorMessage.PAYMENT_NOT_FOUND, disputeDao.get(disputeId).getErrorMessage());
//    }
//
//    @Test
//    @SneakyThrows
//    public void testInvoiceNotFound() {
//        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
//        var dispute = disputeDao.get(disputeId);
//        adjustmentsService.callHgForCreateAdjustment(dispute);
//        assertEquals(DisputeStatus.failed, disputeDao.get(disputeId).getStatus());
//        assertEquals(ErrorMessage.INVOICE_NOT_FOUND, disputeDao.get(disputeId).getErrorMessage());
//    }
//
//    @Test
//    @SneakyThrows
//    public void testFullSuccessFlow() {
//        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
//        var dispute = disputeDao.get(disputeId);
//        dispute.setReason("test adj");
//        dispute.setChangedAmount(dispute.getAmount() + 1);
//        var adjustmentId = "adjustmentId";
//        var reason = adjustmentExtractor.getReason(dispute);
//        when(invoicingClient.createPaymentAdjustment(any(), any(), any()))
//                .thenReturn(getCapturedInvoicePaymentAdjustment(adjustmentId, reason));
//        adjustmentsService.callHgForCreateAdjustment(dispute);
//        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
//        disputeDao.finishFailed(disputeId, null);
//    }
//
//    @Test
//    @SneakyThrows
//    public void testDisputesAdjustmentExist() {
//        var disputeId = pendingDisputesTestService.callPendingDisputeRemotely();
//        var dispute = disputeDao.get(disputeId);
//        dispute.setReason("test adj");
//        dispute.setChangedAmount(dispute.getAmount() + 1);
//        var adjustmentId = "adjustmentId";
//        var invoicePayment = MockUtil.createInvoicePayment(dispute.getPaymentId());
//        invoicePayment.getPayment().setStatus(InvoicePaymentStatus.captured(new InvoicePaymentCaptured()));
//        invoicePayment.setAdjustments(List.of(
//                getCapturedInvoicePaymentAdjustment(adjustmentId, adjustmentExtractor.getReason(dispute)),
//                getCashFlowInvoicePaymentAdjustment(adjustmentId, adjustmentExtractor.getReason(dispute))));
//        when(invoicingClient.getPayment(any(), any())).thenReturn(invoicePayment);
//        adjustmentsService.callHgForCreateAdjustment(dispute);
//        assertEquals(DisputeStatus.succeeded, disputeDao.get(disputeId).getStatus());
//        disputeDao.finishFailed(disputeId, null);
//    }
}
