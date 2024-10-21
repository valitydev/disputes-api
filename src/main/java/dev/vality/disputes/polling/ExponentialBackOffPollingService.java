package dev.vality.disputes.polling;

import dev.vality.adapter.flow.lib.model.PollingInfo;
import dev.vality.adapter.flow.lib.utils.backoff.ExponentialBackOff;

import java.time.Instant;
import java.util.Map;

import static dev.vality.adapter.flow.lib.utils.backoff.ExponentialBackOff.*;

public class ExponentialBackOffPollingService {

    public int prepareNextPollingInterval(PollingInfo pollingInfo, Map<String, String> options) {
        return exponentialBackOff(pollingInfo, options)
                .start()
                .nextBackOff()
                .intValue();
    }

    private ExponentialBackOff exponentialBackOff(PollingInfo pollingInfo, Map<String, String> options) {
        final var currentLocalTime = Instant.now().toEpochMilli();
        var startTime = pollingInfo.getStartDateTimePolling() != null
                ? pollingInfo.getStartDateTimePolling().toEpochMilli()
                : currentLocalTime;
        var exponential = TimeOptionsExtractors.extractExponent(options, DEFAULT_MUTIPLIER);
        var defaultInitialExponential =
                TimeOptionsExtractors.extractDefaultInitialExponential(options, DEFAULT_INITIAL_INTERVAL_SEC);
        var maxTimeBackOff = TimeOptionsExtractors.extractMaxTimeBackOff(options, DEFAULT_MAX_INTERVAL_SEC);
        return new ExponentialBackOff(
                startTime,
                currentLocalTime,
                exponential,
                defaultInitialExponential,
                maxTimeBackOff);
    }
}
