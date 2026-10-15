package dev.vality.disputes.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

/**
 * Points the datasource of a Spring test context at a JVM-wide embedded PostgreSQL, so tests need no Docker.
 * Flyway applies the migrations on context startup as it does for a regular database.
 */
public class EmbeddedPostgresInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String USER = "postgres";
    private static final String DATABASE = "postgres";
    private static final EmbeddedPostgres EMBEDDED_POSTGRES = startPostgres();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(
                "spring.datasource.url=" + EMBEDDED_POSTGRES.getJdbcUrl(USER, DATABASE),
                "spring.datasource.username=" + USER,
                "spring.datasource.password="
        ).applyTo(context);
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start embedded PostgreSQL for tests", ex);
        }
    }
}
