package dev.vality.disputes.api;

import dev.vality.disputes.api.converter.Status200ResponseConverter;
import dev.vality.disputes.api.service.ApiDisputesService;
import dev.vality.disputes.api.service.PaymentParamsBuilder;
import dev.vality.disputes.security.AccessService;
import dev.vality.swag.disputes.model.Create200Response;
import dev.vality.swag.disputes.model.CreateRequest;
import dev.vality.swag.disputes.model.Status200Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength"})
public class DisputesApiDelegateService implements DisputesApiDelegate {

    private final PaymentParamsBuilder paymentParamsBuilder;
    private final AccessService accessService;
    private final ApiDisputesService apiDisputesService;
    private final Status200ResponseConverter status200ResponseConverter;

    @Override
    public ResponseEntity<Create200Response> create(String xRequestID, CreateRequest req) {
        return create(xRequestID, req, true);
    }

    @Override
    public ResponseEntity<Create200Response> create(String xRequestID, CreateRequest req, boolean checkUserAccessData) {
        log.info("-> Req: {}, xRequestID={}, invoiceId={}, paymentId={}, source={}", "/create", xRequestID, req.getInvoiceId(), req.getPaymentId(), checkUserAccessData ? "api" : "merchThrift");
        var accessData = accessService.approveUserAccess(req.getInvoiceId(), req.getPaymentId(), checkUserAccessData);
        // диспут по платежу может быть открытым только один за раз, если существует, отдаем действующий
        var dispute = apiDisputesService.checkExistBeforeCreate(req.getInvoiceId(), req.getPaymentId());
        if (dispute.isPresent()) {
            log.info("<- Res existing: {}, xRequestID={}, invoiceId={}, paymentId={}", "/create", xRequestID, req.getInvoiceId(), req.getPaymentId());
            return ResponseEntity.ok(new Create200Response(String.valueOf(dispute.get().getId())));
        }
        var paymentParams = paymentParamsBuilder.buildGeneralPaymentContext(accessData);
        var disputeId = apiDisputesService.createDispute(req, paymentParams);
        log.info("<- Res: {}, xRequestID={}, invoiceId={}, paymentId={}, source={}", "/create", xRequestID, req.getInvoiceId(), req.getPaymentId(), checkUserAccessData ? "api" : "merchThrift");
        return ResponseEntity.ok(new Create200Response(String.valueOf(disputeId)));
    }

    @Override
    public ResponseEntity<Status200Response> status(String xRequestID, String disputeId) {
        return status(xRequestID, disputeId, true);
    }

    @Override
    public ResponseEntity<Status200Response> status(String xRequestID, String disputeId, boolean checkUserAccessData) {
        var dispute = apiDisputesService.getDispute(disputeId);
        log.info("-> Req: {}, xRequestID={}, invoiceId={}, paymentId={}, disputeId={}, source={}", "/status", xRequestID, dispute.getInvoiceId(), dispute.getPaymentId(), disputeId, checkUserAccessData ? "api" : "merchThrift");
        accessService.approveUserAccess(dispute.getInvoiceId(), dispute.getPaymentId(), checkUserAccessData);
        var body = status200ResponseConverter.convert(dispute);
        log.info("<- Res: {}, xRequestID={}, invoiceId={}, paymentId={}, disputeId={}, source={}", "/status", xRequestID, dispute.getInvoiceId(), dispute.getPaymentId(), disputeId, checkUserAccessData ? "api" : "merchThrift");
        return ResponseEntity.ok(body);
    }
}
