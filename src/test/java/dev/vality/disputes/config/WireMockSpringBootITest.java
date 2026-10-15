package dev.vality.disputes.config;

import dev.vality.disputes.DisputesApiApplication;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.wiremock.spring.EnableWireMock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@DisableScheduling
@ContextConfiguration(initializers = EmbeddedPostgresInitializer.class)
@ExtendWith(DatabaseCleanupExtension.class)
@AutoConfigureMockMvc
@Import(WiremockAddressesHolder.class)
@EnableWireMock
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = DisputesApiApplication.class,
        properties = {"logging.level.WireMock=WARN"})
@DirtiesContext
public @interface WireMockSpringBootITest {
}
