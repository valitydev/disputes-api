package dev.vality.disputes.manualparsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.disputes.admin.ApproveParamsRequest;
import dev.vality.disputes.admin.BindParamsRequest;
import dev.vality.disputes.admin.CancelParamsRequest;
import dev.vality.disputes.admin.ManualParsingServiceSrv;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/debug/disputes-api/manual-parsing"})
@Slf4j
public class DebugManualParsingController {

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
}
