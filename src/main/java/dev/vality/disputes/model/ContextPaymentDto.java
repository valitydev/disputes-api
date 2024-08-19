package dev.vality.disputes.model;

import dev.vality.damsel.proxy_provider.PaymentInfo;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Builder
@Getter
@Setter
public class ContextPaymentDto {

    private PaymentInfo paymentInfo;
    private Object payerSessionInfo;
    private String p2pProviderUrl;
    @ToString.Exclude
    private Map<String, String> options;
    private long cascadeAttempt;
    private PollingInfo cascadePollingInfo;
    private String terminalId;
    private String terminalName;
    private String providerId;
    private String providerName;

}
