package dev.vality.disputes.polling;

import dev.vality.adapter.flow.lib.model.PollingInfo;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class ExponentialBackOffPollingServiceWrapper {

    private final ExponentialBackOffPollingService exponentialBackOffPollingService;

    public ExponentialBackOffPollingServiceWrapper() {
        this.exponentialBackOffPollingService = new ExponentialBackOffPollingService();
    }

    public LocalDateTime prepareNextPollingInterval(PollingInfo pollingInfo, Map<String, String> options) {
        var seconds = exponentialBackOffPollingService.prepareNextPollingInterval(pollingInfo, options);
        return getLocalDateTime(pollingInfo.getStartDateTimePolling().plusSeconds(seconds));
    }

    public LocalDateTime prepareNextPollingInterval(Dispute dispute, Map<String, String> options) {
        var pollingInfo = new PollingInfo();
        var startDateTimePolling = dispute.getCreatedAt().toInstant(ZoneOffset.UTC);
        pollingInfo.setStartDateTimePolling(startDateTimePolling);
        pollingInfo.setMaxDateTimePolling(dispute.getPollingBefore().toInstant(ZoneOffset.UTC));
        var seconds = exponentialBackOffPollingService.prepareNextPollingInterval(pollingInfo, options);
        return getLocalDateTime(dispute.getNextCheckAfter().toInstant(ZoneOffset.UTC).plusSeconds(seconds));
    }

    private LocalDateTime getLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
