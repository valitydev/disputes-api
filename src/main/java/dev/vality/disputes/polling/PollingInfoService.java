package dev.vality.disputes.polling;

import dev.vality.adapter.flow.lib.model.PollingInfo;
import dev.vality.disputes.config.properties.DisputesTimerProperties;
import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.exception.PoolingExpiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static dev.vality.disputes.util.OptionsExtractor.extractMaxTimePolling;

@Service
@RequiredArgsConstructor
public class PollingInfoService {

    private final DisputesTimerProperties timerProperties;

    public PollingInfo initPollingInfo(Map<String, String> options) {
        return initPollingInfo(new PollingInfo(), options);
    }

    public PollingInfo initPollingInfo(PollingInfo pollingInfo, Map<String, String> options) {
        if (pollingInfo == null) {
            pollingInfo = new PollingInfo();
        }
        if (pollingInfo.getStartDateTimePolling() == null) {
            pollingInfo.setStartDateTimePolling(Instant.now());
        }
        var maxDateTimePolling = calcDeadline(pollingInfo, options);
        pollingInfo.setMaxDateTimePolling(maxDateTimePolling);
        return pollingInfo;
    }

    public void checkDeadline(Dispute dispute) {
        if (isDeadline(convert(dispute))) {
            throw new PoolingExpiredException();
        }
    }

    private boolean isDeadline(PollingInfo pollingInfo) {
        var now = Instant.now();
        return now.isAfter(pollingInfo.getMaxDateTimePolling());
    }

    private Instant calcDeadline(PollingInfo pollingInfo, Map<String, String> options) {
        if (pollingInfo.getMaxDateTimePolling() == null) {
            var maxTimePolling = extractMaxTimePolling(options, timerProperties.getMaxTimePollingMin());
            return pollingInfo.getStartDateTimePolling().plus(maxTimePolling, ChronoUnit.MINUTES);
        }
        return pollingInfo.getMaxDateTimePolling();
    }

    private PollingInfo convert(Dispute dispute) {
        return Optional.ofNullable(dispute)
                .map(d -> {
                    var p = new PollingInfo();
                    p.setStartDateTimePolling(d.getCreatedAt().toInstant(ZoneOffset.UTC));
                    p.setMaxDateTimePolling(d.getPollingBefore().toInstant(ZoneOffset.UTC));
                    return p;
                })
                .orElse(new PollingInfo());
    }
}
