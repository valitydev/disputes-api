package dev.vality.disputes.security;

import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.disputes.exception.AuthorizationException;
import dev.vality.disputes.exception.BouncerException;
import dev.vality.disputes.exception.NotFoundException;
import dev.vality.disputes.security.service.BouncerService;
import dev.vality.disputes.security.service.TokenKeeperService;
import dev.vality.disputes.service.external.InvoicingService;
import dev.vality.disputes.utils.PaymentStatusValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static dev.vality.disputes.exception.NotFoundException.Type;

@Slf4j
@RequiredArgsConstructor
@Service
@SuppressWarnings({"LineLength"})
public class AccessService {

    private final InvoicingService invoicingService;
    private final TokenKeeperService tokenKeeperService;
    private final BouncerService bouncerService;

    @Value("${service.bouncer.auth.enabled}")
    private boolean authEnabled;

    public AccessData approveUserAccess(String invoiceId, String paymentId, boolean checkUserAccessData) {
        log.info("Start building AccessData {}{}", invoiceId, paymentId);
        var accessData = buildAccessData(invoiceId, paymentId, checkUserAccessData);
        if (checkUserAccessData) {
            checkUserAccessData(accessData);
        }
        log.debug("Finish building AccessData {}{}", invoiceId, paymentId);
        return accessData;
    }

    private AccessData buildAccessData(String invoiceId, String paymentId, boolean checkUserAccessData) {
        var invoice = invoicingService.getInvoice(invoiceId);
        return AccessData.builder()
                .invoice(invoice)
                .payment(getInvoicePayment(invoice, paymentId))
                .authData(checkUserAccessData ? tokenKeeperService.getAuthData() : null)
                .build();
    }

    private void checkUserAccessData(AccessData accessData) {
        log.info("Check the user's rights to perform dispute operation");
        try {
            var resolution = bouncerService.getResolution(accessData);
            switch (resolution.getSetField()) {
                case FORBIDDEN: {
                    if (authEnabled) {
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
                                    .filter(shop -> shop.getId()
                                            .equals(accessData.getInvoice().getInvoice().getShopId()))
                                    .findFirst()
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
                    throw new BouncerException(String.format("Resolution %s cannot be processed", resolution));
            }
        } catch (Throwable ex) {
            if (authEnabled) {
                throw ex;
            }
            log.warn("Auth error occurred, but bouncer check is disabled: ", ex);
        }
    }

    private InvoicePayment getInvoicePayment(dev.vality.damsel.payment_processing.Invoice invoice, String paymentId) {
        log.debug("Processing invoice: {}", invoice.getInvoice().getId());
        var invoicePayment = invoice.getPayments().stream()
                .filter(p -> paymentId.equals(p.getPayment().getId()) && p.isSetRoute())
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        String.format("Payment with id: %s and filled route not found!", paymentId), Type.PAYMENT));
        log.debug("Processing payment: {}", invoicePayment);
        PaymentStatusValidator.checkStatus(invoicePayment);
        return invoicePayment;
    }
}
