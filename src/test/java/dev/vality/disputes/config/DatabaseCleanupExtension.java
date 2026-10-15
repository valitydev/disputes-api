package dev.vality.disputes.config;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;

/**
 * Empties every table except Flyway's history before each test, because the embedded PostgreSQL is shared by all
 * Spring contexts of the test run and tests reuse the same invoice and payment ids.
 */
public class DatabaseCleanupExtension implements BeforeEachCallback {

    private static final String TABLES_QUERY = """
            SELECT format('%I.%I', schemaname, tablename)
            FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
              AND tablename NOT LIKE 'flyway%'
            """;

    @Override
    public void beforeEach(ExtensionContext context) {
        var dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var tables = jdbcTemplate.queryForList(TABLES_QUERY, String.class);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }
}
