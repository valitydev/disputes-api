package dev.vality.disputes.config;

import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@EmbeddedPostgresWithFlyway
@DisableScheduling
@SpringBootTest
public @interface EmbeddedPostgresWithFlywaySpringBootITest {
}