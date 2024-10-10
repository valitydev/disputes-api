package dev.vality.disputes.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
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
@RequestMapping({"/disputes"})
@Slf4j
public class CancelController {

    private final ManualParsingServiceSrv.Iface manualParsingHandler;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());

    @PostMapping("/cancel")
    @SneakyThrows
    public void cancelPending(@RequestBody String body) {
        log.debug("cancelPending {}", body);
        manualParsingHandler.cancelPending(objectMapper.readValue(body, CancelParamsRequest.class));
    }
}
