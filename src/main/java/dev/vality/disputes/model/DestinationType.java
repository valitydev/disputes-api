package dev.vality.disputes.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DestinationType {

    SBP("SBP"),
    BANK_CARD("BankCard"),
    BANK_ACCOUNT("BankAccount");

    private final String swagValue;

}
