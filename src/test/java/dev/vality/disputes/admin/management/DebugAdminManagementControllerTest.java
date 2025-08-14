package dev.vality.disputes.admin.management;

import dev.vality.disputes.admin.AdminManagementServiceSrv;
import dev.vality.disputes.admin.Attachment;
import dev.vality.disputes.admin.Dispute;
import dev.vality.disputes.admin.DisputeResult;
import dev.vality.disputes.config.SpringBootUTest;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static dev.vality.disputes.util.OpenApiUtil.*;
import static dev.vality.testcontainers.annotations.util.RandomBeans.randomThrift;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootUTest
public class DebugAdminManagementControllerTest {

    @MockitoBean
    private AdminManagementServiceSrv.Iface adminManagementHandler;
    @Autowired
    private DebugAdminManagementController debugAdminManagementController;

    @Test
    @SneakyThrows
    public void checkSerialization() {
        debugAdminManagementController.approvePending(
                getApproveRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString(), true));
        debugAdminManagementController.cancelPending(
                getCancelRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        debugAdminManagementController.bindCreated(
                getBindCreatedRequest(UUID.randomUUID(), UUID.randomUUID().toString()));
        var randomed = new DisputeResult();
        byte[] b = new byte[20];
        new Random().nextBytes(b);
        byte[] a = new byte[20];
        new Random().nextBytes(a);
        randomed.setDisputes(List.of(
                randomThrift(Dispute.class).setAttachments(List.of(new Attachment().setData(b))),
                randomThrift(Dispute.class).setAttachments(List.of(new Attachment().setData(a)))));
        given(adminManagementHandler.getDisputes(any()))
                .willReturn(randomed);
        var disputes = debugAdminManagementController.getDisputes(
                getGetDisputeRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString(), false));
        assertEquals(2, disputes.getDisputes().size());
    }
}
