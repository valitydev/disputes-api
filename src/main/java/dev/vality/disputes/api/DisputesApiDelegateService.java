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
@SuppressWarnings({"LineLength"})
public class DisputesApiDelegateService implements DisputesApiDelegate {

    private final PaymentParamsBuilder paymentParamsBuilder;
    private final AccessService accessService;
    private final ApiDisputesService apiDisputesService;
    private final Status200ResponseConverter status200ResponseConverter;

    @Override
    public ResponseEntity<Create200Response> create(CreateRequest createRequest) {
        return create(createRequest, true);
    }

    @Override
    public ResponseEntity<Create200Response> create(CreateRequest req, boolean checkUserAccessData) {
        log.info("-> Req: {}, invoiceId={}, paymentId={}, source={}", "/create", req.getInvoiceId(), req.getPaymentId(), checkUserAccessData ? "api" : "merchThrift");
        var accessData = accessService.approveUserAccess(req.getInvoiceId(), req.getPaymentId(), checkUserAccessData, true);
        // диспут по платежу может быть открытым только один за раз, если существует, отдаем действующий
        var dispute = apiDisputesService.checkExistBeforeCreate(req.getInvoiceId(), req.getPaymentId());
        if (dispute.isPresent()) {
            log.debug("<- Res existing: {}, invoiceId={}, paymentId={}", "/create", req.getInvoiceId(), req.getPaymentId());
            return ResponseEntity.ok(new Create200Response(String.valueOf(dispute.get().getId())));
        }
        var paymentParams = paymentParamsBuilder.buildGeneralPaymentContext(accessData);
        var disputeId = apiDisputesService.createDispute(req, paymentParams);
        log.debug("<- Res: {}, invoiceId={}, paymentId={}, source={}", "/create", req.getInvoiceId(), req.getPaymentId(), checkUserAccessData ? "api" : "merchThrift");
        return ResponseEntity.ok(new Create200Response(String.valueOf(disputeId)));
    }

    @Override
    public ResponseEntity<Status200Response> status(String disputeId) {
        return status(disputeId, true);
    }

    @Override
    public ResponseEntity<Status200Response> status(String disputeId, boolean checkUserAccessData) {
        var dispute = apiDisputesService.getDispute(disputeId);
        log.info("-> Req: {}, invoiceId={}, paymentId={}, disputeId={}, source={}", "/status", dispute.getInvoiceId(), dispute.getPaymentId(), disputeId, checkUserAccessData ? "api" : "merchThrift");
        accessService.approveUserAccess(dispute.getInvoiceId(), dispute.getPaymentId(), checkUserAccessData, false);
        var body = status200ResponseConverter.convert(dispute);
        log.debug("<- Res: {}, invoiceId={}, paymentId={}, disputeId={}, source={}", "/status", dispute.getInvoiceId(), dispute.getPaymentId(), disputeId, checkUserAccessData ? "api" : "merchThrift");
        return ResponseEntity.ok(body);
    }
}
