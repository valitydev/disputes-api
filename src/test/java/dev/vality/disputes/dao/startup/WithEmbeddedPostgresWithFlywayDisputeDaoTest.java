package dev.vality.disputes.dao.startup;

import dev.vality.disputes.config.EmbeddedPostgresWithFlywaySpringBootITest;
import dev.vality.disputes.dao.DisputeDaoTest;
import org.junit.jupiter.api.Disabled;

@Disabled
@EmbeddedPostgresWithFlywaySpringBootITest
public class WithEmbeddedPostgresWithFlywayDisputeDaoTest extends DisputeDaoTest {
}
