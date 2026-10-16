package dev.vality.disputes.retry;

import dev.vality.woody.api.flow.error.WErrorType;
import dev.vality.woody.api.flow.error.WRuntimeException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.NoHttpResponseException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.Predicate;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransientErrorClassifier {

    public static boolean isTransient(Throwable error) {
        if (containsInterruption(error)) {
            return false;
        }
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (var cause = error; cause != null && seen.size() < 16 && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof WRuntimeException woody && woody.getErrorDefinition() != null) {
                var type = woody.getErrorDefinition().getErrorType();
                return type == WErrorType.UNAVAILABLE_RESULT || type == WErrorType.UNDEFINED_RESULT;
            }
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException
                    || cause instanceof SocketException || cause instanceof UnknownHostException
                    || cause instanceof ConnectionRequestTimeoutException || cause instanceof NoHttpResponseException) {
                return true;
            }
        }
        return false;
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
