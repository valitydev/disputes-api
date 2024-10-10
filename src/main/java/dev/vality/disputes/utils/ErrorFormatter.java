package dev.vality.disputes.utils;

import dev.vality.damsel.domain.Failure;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import org.apache.commons.lang3.StringUtils;

public class ErrorFormatter {

    public static String getErrorMessage(Failure failure) {
        if (!StringUtils.isBlank(failure.getReason())) {
            return failure.getCode() + ": " + failure.getReason();
        }
        return TErrorUtil.toStringVal(failure);
    }
}
