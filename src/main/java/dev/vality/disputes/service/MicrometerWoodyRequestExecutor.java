package dev.vality.disputes.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.util.Optional;
import java.util.function.Function;

@RequiredArgsConstructor
public class MicrometerWoodyRequestExecutor<T> {

    private final String meterName;
    private final String methodName;
    private final MeterRegistry meterRegistry;
    private final Function<T, Optional<String>> failureMapper;

    @SneakyThrows
    public T execute(Supplier<T, Throwable> woodyClient, CustomTags customTags) {
        var timerSample = Timer.start(meterRegistry);
        var mapping = CustomTagsUtils.mappingDefaultSuccess();
        try {
            var response = woodyClient.get();
            mapping = failureMapper.apply(response)
                    .map(CustomTagsUtils::mapping)
                    .orElse(CustomTagsUtils.mappingDefaultSuccess());
            return response;
        } catch (Throwable e) {
            mapping = CustomTagsUtils.mapping(e.getClass().getSimpleName());
            throw e;
        } finally {
            var tags = customTags.tags().and(mapping, CustomTagsUtils.method(methodName));
            timerSample.stop(Timer.builder(meterName)
                    .description("Duration of WoodyClient request execution")
                    .tags(tags)
                    .register(meterRegistry));
        }
    }

    public static class CustomTags {

        private static final String URL_TAG = "url";
        private static final String TERMINAL_ID_TAG = "terminal_id";
        private static final String TERMINAL_NAME_TAG = "terminal_name";
        private static final String PROVIDER_ID_TAG = "provider_id";
        private static final String PROVIDER_NAME_TAG = "provider_name";
        private final Tag url;
        private final Tag terminalId;
        private final Tag terminalName;
        private final Tag providerId;
        private final Tag providerName;

        public CustomTags(String url, String terminalId, String terminalName, String providerId, String providerName) {
            this.url = Tag.of(URL_TAG, url);
            this.terminalId = Tag.of(TERMINAL_ID_TAG, terminalId);
            this.terminalName = Tag.of(TERMINAL_NAME_TAG, terminalName);
            this.providerId = Tag.of(PROVIDER_ID_TAG, providerId);
            this.providerName = Tag.of(PROVIDER_NAME_TAG, providerName);
        }

        public Tags tags() {
            return Tags.of(url, terminalId, terminalName, providerId, providerName);
        }
    }

    public static class CustomTagsUtils {

        private static final String MAPPING_TAG = "mapping";
        private static final String METHOD_TAG = "method";

        public static Tag mappingDefaultSuccess() {
            return Tag.of(MAPPING_TAG, "expected_success_request");
        }

        public static Tag mapping(String mapping) {
            return Tag.of(MAPPING_TAG, mapping);
        }

        public static Tag method(String method) {
            return Tag.of(METHOD_TAG, method);
        }
    }

    @FunctionalInterface
    public interface Supplier<R, E extends Throwable> {

        R get() throws E;

    }
}
