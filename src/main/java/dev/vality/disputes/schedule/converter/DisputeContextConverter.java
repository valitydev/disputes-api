package dev.vality.disputes.schedule.converter;

import dev.vality.disputes.DisputeContext;
import dev.vality.disputes.domain.tables.pojos.ProviderDispute;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DisputeContextConverter {

    public DisputeContext convert(ProviderDispute providerDispute, Map<String, String> options) {
        var disputeContext = new DisputeContext();
        disputeContext.setDisputeId(providerDispute.getProviderDisputeId());
        disputeContext.setTerminalOptions(options);
        return disputeContext;
    }
}
