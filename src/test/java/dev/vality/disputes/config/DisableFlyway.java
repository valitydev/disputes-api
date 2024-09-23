package dev.vality.disputes.config;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
})
@MockBean(DataSource.class)
public @interface DisableFlyway {
}
