package dev.vality.disputes.polling;

import dev.vality.adapter.flow.lib.model.PollingInfo;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.Notification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@SuppressWarnings({"LineLength"})
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
        var seconds = exponentialBackOffPollingService.prepareNextPollingInterval(pollingInfo, options);
        return getLocalDateTime(dispute.getNextCheckAfter().toInstant(ZoneOffset.UTC).plusSeconds(seconds));
    }

    public LocalDateTime prepareNextPollingInterval(Notification notification, LocalDateTime createdAt, Map<String, String> options) {
        var pollingInfo = new PollingInfo();
        pollingInfo.setStartDateTimePolling(createdAt.toInstant(ZoneOffset.UTC));
        var seconds = exponentialBackOffPollingService.prepareNextPollingInterval(pollingInfo, options);
        return getLocalDateTime(
                notification.getNextAttemptAfter().toInstant(ZoneOffset.UTC).plusSeconds(seconds));
    }

    private LocalDateTime getLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
