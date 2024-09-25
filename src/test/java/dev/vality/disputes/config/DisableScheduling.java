package dev.vality.disputes.config;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = {
        "dispute.isScheduleCreatedEnabled=false",
        "dispute.isSchedulePendingEnabled=false",
        "dispute.isScheduleCreateAdjustmentsEnabled=false",
        "dispute.isScheduleReadyForCreateAdjustmentsEnabled=false",
})
public @interface DisableScheduling {
}
