package dev.vality.disputes.dao.config.testconfiguration;

import dev.vality.disputes.schedule.TaskCreateAdjustmentsService;
import dev.vality.disputes.schedule.TaskCreatedDisputesService;
import dev.vality.disputes.schedule.TaskPendingDisputesService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestConfiguration
public class MockedUnimportantServicesConfig {

    @MockBean
    private TaskCreatedDisputesService taskCreatedDisputesService;

    @MockBean
    private TaskPendingDisputesService taskPendingDisputesService;

    @MockBean
    private TaskCreateAdjustmentsService taskCreateAdjustmentsService;

}
