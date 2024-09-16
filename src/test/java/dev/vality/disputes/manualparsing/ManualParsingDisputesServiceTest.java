package dev.vality.disputes.manualparsing;

import dev.vality.disputes.admin.ApproveParams;
import dev.vality.disputes.admin.ApproveParamsRequest;
import dev.vality.testcontainers.annotations.util.ThriftUtil;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

class ManualParsingDisputesServiceTest {

    @Test
    public void asd() {
        var approveParamsRequest = new ApproveParamsRequest();
        approveParamsRequest.setApproveParams(List.of(new ApproveParams("7", true)));
        System.out.println(ThriftUtil.thriftToJson(approveParamsRequest));
        System.out.println(Instant.now().plus(1, ChronoUnit.DAYS).toString());
    }

}