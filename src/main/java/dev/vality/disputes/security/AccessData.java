package dev.vality.disputes.security;

import dev.vality.damsel.payment_processing.Invoice;
import dev.vality.damsel.payment_processing.InvoicePayment;
import dev.vality.token.keeper.AuthData;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.ToString;

@Builder
@Data
@Setter
public class AccessData {

    private Invoice invoice;
    private InvoicePayment payment;
    @ToString.Exclude
    private AuthData authData;

}
