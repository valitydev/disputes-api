package dev.vality.disputes.utils;

import lombok.experimental.UtilityClass;

import java.util.Map;

import static dev.vality.disputes.constant.TerminalOptionsField.DISPUTE_FLOW_MAX_TIME_POLLING_MIN;

@UtilityClass
public class OptionsExtractors {

    public static Integer extractMaxTimePolling(Map<String, String> options, int maxTimePolling) {
        return Integer.parseInt(
                options.getOrDefault(DISPUTE_FLOW_MAX_TIME_POLLING_MIN, String.valueOf(maxTimePolling)));
    }
}
