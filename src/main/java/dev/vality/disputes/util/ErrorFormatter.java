package dev.vality.disputes.util;

import dev.vality.damsel.domain.Failure;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class ErrorFormatter {

    public static String getErrorMessage(Failure failure) {
        return decodeFragment(getDefaultErrorMessage(failure));
    }

    public static String getErrorMessage(String errorCode, String errorDescription) {
        return decodeFragment(getDefaultErrorMessage(errorCode, errorDescription));
    }

    private static String getDefaultErrorMessage(Failure failure) {
        if (!StringUtils.isBlank(failure.getReason())) {
            return failure.getCode() + ": " + failure.getReason();
        }
        return TErrorUtil.toStringVal(failure);
    }

    private static String getDefaultErrorMessage(String errorCode, String errorDescription) {
        if (!StringUtils.isBlank(errorDescription)) {
            return errorCode + ": " + errorDescription;
        }
        return errorCode;
    }

    private static String decodeFragment(String errorMessage) {
        if (!errorMessage.contains("base64:")) {
            return errorMessage;
        }
        var pattern = Pattern.compile("base64:([A-Za-z0-9+/=]+)");
        var matcher = pattern.matcher(errorMessage);
        var result = new StringBuilder();
        while (matcher.find()) {
            var base64String = matcher.group(1);
            var decodedBytes = Base64.getDecoder().decode(base64String);
            var decodedString = new String(decodedBytes);
            matcher.appendReplacement(result, Matcher.quoteReplacement(decodedString));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
