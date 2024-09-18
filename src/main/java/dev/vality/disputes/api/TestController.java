package dev.vality.disputes.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.disputes.admin.ApproveParamsRequest;
import dev.vality.disputes.admin.BindParamsRequest;
import dev.vality.disputes.admin.CancelParamsRequest;
import dev.vality.disputes.admin.ManualParsingServiceSrv;
import dev.vality.geck.serializer.kit.json.JsonProcessor;
import dev.vality.geck.serializer.kit.tbase.TBaseHandler;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TBase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/test/disputes-api/manual-parsing"})
@Slf4j
public class TestController {

    private final ManualParsingServiceSrv.Iface manualParsingHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/cancel")
    @SneakyThrows
    public void cancelPending(@RequestBody String body) {
        log.debug("cancelPending {}", body);
        manualParsingHandler.cancelPending(objectMapper.readValue(body, CancelParamsRequest.class));
    }

    @PostMapping("/approve")
    @SneakyThrows
    public void approvePending(@RequestBody String body) {
        log.debug("approvePending {}", body);
        manualParsingHandler.approvePending(objectMapper.readValue(body, ApproveParamsRequest.class));
    }

    @PostMapping("/bind")
    @SneakyThrows
    public void bindCreated(@RequestBody String body) {
        log.debug("bindCreated {}", body);
        manualParsingHandler.bindCreated(objectMapper.readValue(body, BindParamsRequest.class));
    }

    @SneakyThrows
    public static <T extends TBase> T jsonToThrift(JsonNode jsonNode, Class<T> type) {
        return new JsonProcessor().process(jsonNode, new TBaseHandler<>(type));
    }
}
