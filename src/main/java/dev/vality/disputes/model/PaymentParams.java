package dev.vality.disputes.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Builder
@Getter
@Setter
public class PaymentParams {

    private String invoiceId;
    private String paymentId;
    @ToString.Exclude
    private Map<String, String> options;
    private String terminalId;
    private String providerId;

}
