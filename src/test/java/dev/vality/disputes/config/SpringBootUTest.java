package dev.vality.disputes.config;

import dev.vality.testcontainers.annotations.DefaultSpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@DisableScheduling
@DisableFlyway
@DefaultSpringBootTest
public @interface SpringBootUTest {
}