package dev.vality.disputes.config;

import dev.vality.disputes.DisputesApiApplication;
import dev.vality.testcontainers.annotations.postgresql.PostgresqlTestcontainerSingleton;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.wiremock.spring.EnableWireMock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@DisableScheduling
@PostgresqlTestcontainerSingleton
@AutoConfigureMockMvc
@Import(WiremockAddressesHolder.class)
@EnableWireMock
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = DisputesApiApplication.class,
        properties = {"logging.level.WireMock=WARN"})
public @interface WireMockSpringBootITest {
}
