package dev.vality.disputes.polling;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TimeOptionsExtractors {

    public static final String DISPUTE_TIMER_EXPONENTIAL = "dispute_exponential";
    public static final String DISPUTE_MAX_TIME_BACKOFF_SEC = "dispute_max_time_backoff_sec";
    public static final String DISPUTE_DEFAULT_INITIAL_EXPONENTIAL_SEC = "dispute_default_initial_exponential_sec";

    public static Integer extractExponent(Map<String, String> options, int defaultExponent) {
        return Integer.parseInt(options.getOrDefault(
                DISPUTE_TIMER_EXPONENTIAL, String.valueOf(defaultExponent)));
    }

    public static Integer extractMaxTimeBackOff(Map<String, String> options, int maxTimeBackOff) {
        return Integer.parseInt(options.getOrDefault(
                DISPUTE_MAX_TIME_BACKOFF_SEC, String.valueOf(maxTimeBackOff)));
    }

    public static Integer extractDefaultInitialExponential(Map<String, String> options, int defaultInitialExponential) {
        return Integer.parseInt(options.getOrDefault(
                DISPUTE_DEFAULT_INITIAL_EXPONENTIAL_SEC, String.valueOf(defaultInitialExponential)));
    }
}
