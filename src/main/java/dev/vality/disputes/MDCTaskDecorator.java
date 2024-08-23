package dev.vality.disputes;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

@Slf4j
@SuppressWarnings("AbbreviationAsWordInName")
public class MDCTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        var contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
