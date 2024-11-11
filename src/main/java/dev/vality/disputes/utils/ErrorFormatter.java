package dev.vality.disputes.utils;

import dev.vality.damsel.domain.Failure;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class ErrorFormatter {

    public static String getErrorMessage(Failure failure) {
        if (!StringUtils.isBlank(failure.getReason())) {
            return failure.getCode() + ": " + failure.getReason();
        }
        return TErrorUtil.toStringVal(failure);
    }

    public static String getErrorMessage(String errorCode, String errorDescription) {
        if (!StringUtils.isBlank(errorDescription)) {
            return errorCode + ": " + errorDescription;
        }
        return errorCode;
    }
}
