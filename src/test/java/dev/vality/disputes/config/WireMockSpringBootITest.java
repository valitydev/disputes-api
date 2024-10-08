package dev.vality.disputes.config;

import dev.vality.disputes.DisputesApiApplication;
import dev.vality.testcontainers.annotations.postgresql.PostgresqlTestcontainerSingleton;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@DisableScheduling
@PostgresqlTestcontainerSingleton
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = DisputesApiApplication.class,
        properties = {
                "wiremock.server.baseUrl=http://localhost:${wiremock.server.port}",
                "logging.level.WireMock=WARN"})
public @interface WireMockSpringBootITest {
}
