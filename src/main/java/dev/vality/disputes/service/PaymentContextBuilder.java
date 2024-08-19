package dev.vality.disputes.service;

import dev.vality.disputes.exception.RoutingException;
import dev.vality.disputes.model.ContextPaymentDto;
import dev.vality.disputes.security.AccessData;
import dev.vality.disputes.service.external.DominantService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URL;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentContextBuilder {

    private static final String P2P_URL_POSTFIX_DEFAULT = "p2p";
    private static final String OPTION_P2P_URL_FIELD_NAME = "p2p_url";

    private final PaymentInfoExtractor paymentInfoExtractor;
    private final DominantService dominantService;

    @SneakyThrows
    public ContextPaymentDto buildGeneralPaymentContext(AccessData accessData) {
        log.debug("Start building generalPaymentContext");
        var invoice = accessData.getInvoice();
        var payment = accessData.getPayment();
        var paymentInfo = paymentInfoExtractor.getPaymentInfo(invoice, payment);

        var provider = dominantService.getProvider(payment.getRoute().getProvider());
        var terminal = dominantService.getTerminal(payment.getRoute().getTerminal());
        var proxy = dominantService.getProxy(provider.get().getProxy().getRef());

        log.debug("Finish building generalPaymentContext");
        return ContextPaymentDto.builder()
                .paymentInfo(paymentInfo.get())
                .options(terminal.get().getOptions())
                .p2pProviderUrl(getP2PProviderUrl(terminal.get().getOptions(), proxy.get().getUrl()))
                .terminalId(String.valueOf(payment.getRoute().getTerminal().getId()))
                .terminalName(terminal.get().getName())
                .providerId(String.valueOf(payment.getRoute().getProvider().getId()))
                .providerName(provider.get().getName())
                .build();
    }

    private String getP2PProviderUrl(Map<String, String> options, String defaultProviderUrl) {
        var url = options.get(OPTION_P2P_URL_FIELD_NAME);
        if (ObjectUtils.isEmpty(url)) {
            url = createDefaultP2PProviderUrl(defaultProviderUrl);
        }
        return url;
    }

    private String createDefaultP2PProviderUrl(String defaultProviderUrl) {
        log.debug("Creating url by appending postfix");
        try {
            var validUri = new URL(defaultProviderUrl).toURI();
            return UriComponentsBuilder.fromUri(validUri)
                    .pathSegment(P2P_URL_POSTFIX_DEFAULT)
                    .encode()
                    .build()
                    .toUriString();
        } catch (Exception e) {
            throw new RoutingException("Unable to create default provider url: ", e);
        }
    }
}
