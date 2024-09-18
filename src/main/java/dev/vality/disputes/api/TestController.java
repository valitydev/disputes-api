package dev.vality.disputes.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vality.disputes.admin.ApproveParamsRequest;
import dev.vality.disputes.admin.BindParamsRequest;
import dev.vality.disputes.admin.CancelParamsRequest;
import dev.vality.disputes.admin.ManualParsingServiceSrv;
import dev.vality.geck.serializer.kit.json.JsonProcessor;
import dev.vality.geck.serializer.kit.tbase.TBaseHandler;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.thrift.TBase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/test/disputes-api/manual-parsing"})
public class TestController {

    private final ManualParsingServiceSrv.Iface manualParsingHandler;

    @PostMapping("/cancel")
    @SneakyThrows
    public void cancelPending(@RequestBody JsonNode body) {
        manualParsingHandler.cancelPending(jsonToThrift(body, CancelParamsRequest.class));
    }

    @PostMapping("/approve")
    @SneakyThrows
    public void approvePending(@RequestBody JsonNode body) {
        manualParsingHandler.approvePending(jsonToThrift(body, ApproveParamsRequest.class));
    }

    @PostMapping("/bind")
    @SneakyThrows
    public void bindCreated(@RequestBody JsonNode body) {
        manualParsingHandler.bindCreated(jsonToThrift(body, BindParamsRequest.class));
    }

    @SneakyThrows
    public static <T extends TBase> T jsonToThrift(JsonNode jsonNode, Class<T> type) {
        return new JsonProcessor().process(jsonNode, new TBaseHandler<>(type));
    }
}
