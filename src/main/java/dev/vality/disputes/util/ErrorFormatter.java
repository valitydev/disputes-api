package dev.vality.disputes.util;

import dev.vality.damsel.domain.Failure;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.vality.disputes.constant.ErrorMessage.PROVIDER_RESULT_UNEXPECTED;

@UtilityClass
public class ErrorFormatter {

    public static String getProviderMessage(Failure failure) {
        return getDefaultErrorMessage(failure);
    }

    public static String getErrorMessage(String errorMessage) {
        return getDefaultErrorMessage(errorMessage);
    }

    private static String getDefaultErrorMessage(Failure failure) {
        if (!StringUtils.isBlank(failure.getReason())) {
            return failure.getReason();
        }
        return TErrorUtil.toStringVal(failure);
    }

    private static String getDefaultErrorMessage(String errorMessage) {
        if (!StringUtils.isBlank(errorMessage)) {
            return decodeFragment(errorMessage);
        }
        return PROVIDER_RESULT_UNEXPECTED;
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
