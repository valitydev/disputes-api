package dev.vality.disputes.config;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
})
//@MockitoBean(types = {DataSource.class}) spring-test:6.2.2+ -> bump service-parent-pom
public @interface DisableFlyway {
}
