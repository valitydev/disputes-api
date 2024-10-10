package dev.vality.disputes.api.converter;

import dev.vality.adapter.flow.lib.model.PollingInfo;
import dev.vality.disputes.api.model.PaymentParams;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.polling.ExponentialBackOffPollingServiceWrapper;
import dev.vality.disputes.polling.PollingInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class DisputeConverter {

    private final PollingInfoService pollingInfoService;
    private final ExponentialBackOffPollingServiceWrapper exponentialBackOffPollingService;

    public Dispute convert(PaymentParams paymentParams, Long amount, String reason) {
        var pollingInfo = pollingInfoService.initPollingInfo((Dispute) null, paymentParams.getOptions());
        var dispute = new Dispute();
        dispute.setCreatedAt(getLocalDateTime(pollingInfo.getStartDateTimePolling()));
        dispute.setNextCheckAfter(getNextCheckAfter(paymentParams, pollingInfo));
        dispute.setPollingBefore(getLocalDateTime(pollingInfo.getMaxDateTimePolling()));
        dispute.setInvoiceId(paymentParams.getInvoiceId());
        dispute.setPaymentId(paymentParams.getPaymentId());
        dispute.setProviderId(paymentParams.getProviderId());
        dispute.setTerminalId(paymentParams.getTerminalId());
        dispute.setProviderTrxId(paymentParams.getProviderTrxId());
        dispute.setAmount(amount == null ? paymentParams.getInvoiceAmount() : amount);
        dispute.setCurrencyName(paymentParams.getCurrencyName());
        dispute.setCurrencySymbolicCode(paymentParams.getCurrencySymbolicCode());
        dispute.setCurrencyNumericCode(paymentParams.getCurrencyNumericCode());
        dispute.setCurrencyExponent(paymentParams.getCurrencyExponent());
        dispute.setReason(reason);
        dispute.setShopId(paymentParams.getShopId());
        dispute.setShopDetailsName(paymentParams.getShopDetailsName());
        return dispute;
    }

    private LocalDateTime getNextCheckAfter(PaymentParams paymentParams, PollingInfo pollingInfo) {
        return exponentialBackOffPollingService.prepareNextPollingInterval(pollingInfo, paymentParams.getOptions());
    }

    private LocalDateTime getLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
