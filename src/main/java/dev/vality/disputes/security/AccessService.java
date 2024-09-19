package dev.vality.disputes.security;

import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.exception.AuthorizationException;
import dev.vality.disputes.exception.BouncerException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.security.service.BouncerService;
import dev.vality.disputes.security.service.TokenKeeperService;
import dev.vality.disputes.service.external.InvoicingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class AccessService {

    private final InvoicingService invoicingService;
    private final TokenKeeperService tokenKeeperService;
    private final BouncerService bouncerService;

    @Value("${service.bouncer.auth.enabled}")
    private boolean authEnabled;

    public AccessData approveUserAccess(String invoiceId, String paymentId) {
        log.info("Start building AccessData {}{}", invoiceId, paymentId);
        var accessData = buildAccessData(invoiceId, paymentId);
        checkUserAccessData(accessData);
        log.debug("Finish building AccessData {}{}", invoiceId, paymentId);
        return accessData;
    }

    private AccessData buildAccessData(String invoiceId, String paymentId) {
        // http 500
        var invoice = invoicingService.getInvoice(invoiceId);
        return AccessData.builder()
                .invoice(invoice)
                .payment(getInvoicePayment(invoice, paymentId))
                // http 500
                .authData(tokenKeeperService.getAuthData())
                .build();
    }

    private void checkUserAccessData(AccessData accessData) {
        log.info("Check the user's rights to perform dispute operation");
        try {
            // http 500
            var resolution = bouncerService.getResolution(accessData);
            switch (resolution.getSetField()) {
                case FORBIDDEN: {
                    if (authEnabled) {
                        // http 500
                        throw new AuthorizationException("No rights to perform dispute");
                    } else {
                        log.warn("No rights to perform dispute operation, but auth is disabled");
                    }
                }
                break;
                case RESTRICTED: {
                    if (authEnabled) {
                        var restrictions = resolution.getRestricted().getRestrictions();
                        if (restrictions.isSetCapi()) {
                            restrictions.getCapi().getOp().getShops().stream()
                                    .filter(shop ->
                                            shop.getId().equals(accessData.getInvoice().getInvoice().getShopId()))
                                    .findFirst()
                                    // http 500
                                    .orElseThrow(() -> new AuthorizationException("No rights to perform dispute"));
                        }
                    } else {
                        log.warn("Rights to perform dispute are restricted, but auth is disabled");
                    }
                }
                break;
                case ALLOWED:
                    break;
                default:
                    // http 500
                    throw new BouncerException(String.format("Resolution %s cannot be processed", resolution));
            }
        } catch (Exception e) {
            if (authEnabled) {
                // http 500
                throw e;
            }
            log.warn("Auth error occurred, but bouncer check is disabled: ", e);
        }
    }

    private InvoicePayment getInvoicePayment(dev.vality.damsel.payment_processing.Invoice invoice, String paymentId) {
        log.debug("Processing invoice: {}", invoice.getInvoice().getId());
        return invoice.getPayments().stream()
                .filter(invoicePayment -> paymentId.equals(invoicePayment.getPayment().getId())
                        && invoicePayment.isSetRoute())
                .findFirst()
                // http 404
                .orElseThrow(() -> new NotFoundException(
                        String.format("Payment with id: %s and filled route not found!", paymentId)));
    }
}
