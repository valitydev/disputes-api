package dev.vality.disputes.config;

import io.zonky.test.db.postgres.embedded.FlywayPreparer;
import io.zonky.test.db.postgres.embedded.PreparedDbProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.SQLException;

@TestConfiguration
public class EmbeddedPostgresWithFlywayConfiguration {

    @Bean
    public DataSource dataSource() throws SQLException {
        return PreparedDbProvider
                .forPreparer(FlywayPreparer.forClasspathLocation("db/migration"))
                .createDataSource();
    }
}
