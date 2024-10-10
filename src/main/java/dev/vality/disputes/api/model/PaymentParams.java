package dev.vality.disputes.api.model;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.Map;

@Builder
@Data
public class PaymentParams {

    private String invoiceId;
    private String paymentId;
    private Integer terminalId;
    private Integer providerId;
    private String providerTrxId;
    private String currencyName;
    private String currencySymbolicCode;
    private Integer currencyNumericCode;
    private Integer currencyExponent;
    @ToString.Exclude
    private Map<String, String> options;
    private String shopId;
    private String shopDetailsName;

}
