package dev.vality.disputes.manualparsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.disputes.admin.ApproveParams;
import dev.vality.disputes.admin.ApproveParamsRequest;
import dev.vality.geck.serializer.kit.json.JsonProcessor;
import dev.vality.geck.serializer.kit.tbase.TBaseHandler;
import dev.vality.testcontainers.annotations.util.ThriftUtil;
import lombok.SneakyThrows;
import org.apache.thrift.TBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

class ManualParsingDisputesServiceTest {

    @Test
    public void name() {
        var approveParamsRequest = new ApproveParamsRequest();
        approveParamsRequest.setApproveParams(List.of(new ApproveParams("7", true)));
        System.out.println(ThriftUtil.thriftToJson(approveParamsRequest));
        System.out.println(Instant.now().plus(1, ChronoUnit.DAYS).toString());
    }

    @Test
    @SneakyThrows
    public void asd() {
        System.out.println(fromJson("{\"approveParams\":[\"list\",{\"disputeId\":\"7\",\"skipCallHgForCreateAdjustment\":true}]}", ApproveParamsRequest.class));
        ApproveParamsRequest approveParamsRequest = new ObjectMapper().readValue(
                "{\"approveParams\":[{\"disputeId\":\"7\",\"skipCallHgForCreateAdjustment\":true}]}",
                ApproveParamsRequest.class);
        System.out.println(approveParamsRequest);
    }

    @SneakyThrows
    public static <T extends TBase> T fromJson(String jsonString, Class<T> type) {
        return new JsonProcessor().process(new ObjectMapper().readTree(jsonString), new TBaseHandler<>(type));
    }

}
