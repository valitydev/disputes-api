package dev.vality.disputes.api;

import dev.vality.disputes.api.converter.Status200ResponseConverter;
import dev.vality.disputes.api.service.ApiDisputeService;
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
    private final ApiDisputeService apiDisputeService;
    private final Status200ResponseConverter status200ResponseConverter;

    @Override
    public ResponseEntity<Create200Response> create(String xRequestID, CreateRequest req) {
        log.info("-> Req: {}, xRequestID={}, invoiceId={}, paymentId={}", "/create", xRequestID, req.getInvoiceId(), req.getPaymentId());
        var accessData = accessService.approveUserAccess(req.getInvoiceId(), req.getPaymentId());
        // диспут по платежу может быть открытым только один за раз, если существует, отдаем действующий
        var dispute = apiDisputeService.checkExistBeforeCreate(req.getInvoiceId(), req.getPaymentId());
        if (dispute.isPresent()) {
            log.info("<- Res existing: {}, xRequestID={}, invoiceId={}, paymentId={}", "/create", xRequestID, req.getInvoiceId(), req.getPaymentId());
            return ResponseEntity.ok(new Create200Response(String.valueOf(dispute.get().getId())));
        }
        var paymentParams = paymentParamsBuilder.buildGeneralPaymentContext(accessData);
        var disputeId = apiDisputeService.createDispute(req, paymentParams);
        log.info("<- Res: {}, xRequestID={}, invoiceId={}, paymentId={}", "/create", xRequestID, req.getInvoiceId(), req.getPaymentId());
        return ResponseEntity.ok(new Create200Response(String.valueOf(disputeId)));
    }

    @Override
    public ResponseEntity<Status200Response> status(String xRequestID, String invoiceId, String paymentId, String disputeId) {
        log.info("-> Req: {}, xRequestID={}, invoiceId={}, paymentId={}, disputeId={}", "/status", xRequestID, invoiceId, paymentId, disputeId);
        accessService.approveUserAccess(invoiceId, paymentId);
        var dispute = apiDisputeService.getDispute(disputeId, invoiceId, paymentId);
        var body = status200ResponseConverter.convert(dispute);
        log.info("<- Res: {}, xRequestID={}, invoiceId={}, paymentId={}, disputeId={}", "/status", xRequestID, invoiceId, paymentId, disputeId);
        return ResponseEntity.ok(body);
    }
}
