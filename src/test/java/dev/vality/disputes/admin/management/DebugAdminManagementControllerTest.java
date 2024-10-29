package dev.vality.disputes.admin.management;

import dev.vality.disputes.admin.AdminManagementServiceSrv;
import dev.vality.disputes.admin.Attachment;
import dev.vality.disputes.admin.Dispute;
import dev.vality.disputes.admin.DisputeResult;
import dev.vality.disputes.config.SpringBootUTest;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Random;

import static dev.vality.testcontainers.annotations.util.RandomBeans.randomThrift;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootUTest
public class DebugAdminManagementControllerTest {

    @MockBean
    private AdminManagementServiceSrv.Iface adminManagementHandler;
    @Autowired
    private DebugAdminManagementController debugAdminManagementController;

    @Test
    @SneakyThrows
    public void checkSerialization() {
        debugAdminManagementController.approvePending("""
                {
                  "approveParams": [
                    {
                      "disputeId": "38",
                      "skipCallHgForCreateAdjustment": true
                    }
                  ]
                }
                """);
        debugAdminManagementController.cancelPending("""
                {
                  "cancelParams": [
                    {
                      "disputeId": "39",
                      "cancelReason": "test endpoint"
                    }
                  ]
                }
                """);
        debugAdminManagementController.cancelPending("""
                {
                  "cancelParams": [
                    {
                      "disputeId": "39",
                      "cancelReason": "test endpoint"
                    }
                  ]
                }
                """);
        debugAdminManagementController.bindCreated("""
                  {
                    "bindParams": [
                      {
                        "disputeId": "36",
                        "providerDisputeId": "66098"
                      }
                    ]
                  }
                """);
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
        var disputes = debugAdminManagementController.getDisputes("""
                  {
                    "disputeParams": [
                      {
                        "disputeId": "38"
                      }
                    ],
                    "withAttachments": false
                  }
                """);
        assertEquals(2, disputes.getDisputes().size());
    }
}
