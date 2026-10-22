package dev.vality.disputes.util;

import dev.vality.woody.api.flow.error.WErrorDefinition;
import dev.vality.woody.api.flow.error.WErrorSource;
import dev.vality.woody.api.flow.error.WErrorType;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.thrift.transport.TTransportException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RemoteErrors {

    private static final Set<String> APACHE_CONNECTION_ERRORS = Set.of(
            ConnectTimeoutException.class.getName(), HttpHostConnectException.class.getName(),
            ConnectionRequestTimeoutException.class.getName());

    public static boolean isTransient(Throwable error) {
        if (containsInterruption(error)) {
            return false;
        }
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (var cause = error; cause != null && seen.size() < 16 && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof WRuntimeException woody && woody.getErrorDefinition() != null) {
                var type = woody.getErrorDefinition().getErrorType();
                return type == WErrorType.UNAVAILABLE_RESULT || type == WErrorType.UNDEFINED_RESULT
                        || isNativeConnectionFailure(woody.getErrorDefinition());
            }
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException
                    || cause instanceof SocketException || cause instanceof UnknownHostException
                    || cause instanceof ConnectionRequestTimeoutException || cause instanceof NoHttpResponseException) {
                return true;
            }
        }
        return false;
    }

    public static boolean isExternalUnexpectedError(WRuntimeException error) {
        var definition = error.getErrorDefinition();
        return definition != null
                && definition.getGenerationSource() == WErrorSource.EXTERNAL
                && definition.getErrorType() == WErrorType.UNEXPECTED_ERROR
                && definition.getErrorSource() == WErrorSource.INTERNAL;
    }

    public static boolean isUnexpectedResultMapping(WRuntimeException error) {
        if (!isExternalUnexpectedError(error)) {
            return false;
        }
        var reason = error.getErrorDefinition().getErrorReason();
        return reason != null && reason.contains("code = ") && reason.contains("description = ");
    }

    public static String getTechnicalMessage(WRuntimeException error) {
        return ErrorFormatter.getErrorMessage(error.getErrorDefinition().getErrorReason());
    }

    private static boolean isNativeConnectionFailure(WErrorDefinition error) {
        // Woody 3 drops the cause for these errors. Match its local transport envelope and exact exception class,
        // not arbitrary provider/business messages containing "timeout".
        if (error.getErrorType() != WErrorType.PROVIDER_ERROR
                || error.getGenerationSource() != WErrorSource.INTERNAL
                || error.getErrorSource() != WErrorSource.INTERNAL
                || !TTransportException.class.getSimpleName().equals(error.getErrorName())
                || error.getErrorMessage() == null) {
            return false;
        }
        var message = error.getErrorMessage();
        var separator = message.indexOf(':');
        return APACHE_CONNECTION_ERRORS.contains(separator < 0 ? message : message.substring(0, separator));
    }

    public static boolean containsInterruption(Throwable error) {
        return anyCause(error, cause -> cause instanceof InterruptedException
                || cause instanceof ClosedByInterruptException);
    }

    private static boolean anyCause(Throwable error, Predicate<Throwable> predicate) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (var cause = error; cause != null && seen.size() < 16 && seen.add(cause); cause = cause.getCause()) {
            if (predicate.test(cause)) {
                return true;
            }
        }
        return false;
    }
}
