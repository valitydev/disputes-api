package dev.vality.disputes.admin.management;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import dev.vality.disputes.admin.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/debug/v1/admin-management"})
@Slf4j
public class DebugAdminManagementController {

    private final AdminManagementServiceSrv.Iface adminManagementHandler;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());

    @PostMapping("/cancel")
    @SneakyThrows
    public void cancelPending(@RequestBody String body) {
        log.debug("cancelPending {}", body);
        adminManagementHandler.cancelPending(objectMapper.readValue(body, CancelParamsRequest.class));
    }

    @PostMapping("/approve")
    @SneakyThrows
    public void approvePending(@RequestBody String body) {
        log.debug("approvePending {}", body);
        adminManagementHandler.approvePending(objectMapper.readValue(body, ApproveParamsRequest.class));
    }

    @PostMapping("/bind")
    @SneakyThrows
    public void bindCreated(@RequestBody String body) {
        log.debug("bindCreated {}", body);
        adminManagementHandler.bindCreated(objectMapper.readValue(body, BindParamsRequest.class));
    }

    @PostMapping("/get")
    @SneakyThrows
    public DisputeResult getDisputes(@RequestBody String body) {
        log.debug("getDispute {}", body);
        var dispute = adminManagementHandler.getDisputes(objectMapper.readValue(body, DisputeParamsRequest.class));
        return objectMapper.convertValue(dispute, new TypeReference<>() {
        });
    }

    @PostMapping("/pooling-expired")
    @SneakyThrows
    public void setPendingForPoolingExpired(@RequestBody String body) {
        log.debug("setPendingForPoolingExpired {}", body);
        adminManagementHandler.setPendingForPoolingExpired(
                objectMapper.readValue(body, SetPendingForPoolingExpiredParamsRequest.class));
    }

    @GetMapping("/disputes")
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void defaultRouteUrl() {
        log.info("hi");
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DisputeResult {

        @JsonProperty("disputes")
        private List<Dispute> disputes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Dispute {

        @JsonProperty("disputeId")
        private String disputeId; // required
        @JsonProperty("providerDisputeId")
        private String providerDisputeId; // optional
        @JsonProperty("invoiceId")
        private String invoiceId; // required
        @JsonProperty("paymentId")
        private String paymentId; // required
        @JsonProperty("providerTrxId")
        private String providerTrxId; // required
        @JsonProperty("status")
        private String status; // required
        @JsonProperty("errorMessage")
        private String errorMessage; // optional
        @JsonProperty("amount")
        private String amount; // required
        @JsonProperty("changedAmount")
        private String changedAmount; // optional
        @JsonProperty("skipCallHgForCreateAdjustment")
        private boolean skipCallHgForCreateAdjustment; // required
        @JsonProperty("attachments")
        @JsonDeserialize(using = AttachmentsDeserializer.class)
        public List<Attachment> attachments;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Attachment {

        private String data;

    }

    public static class AttachmentsDeserializer extends JsonDeserializer<List<Attachment>> {

        @Override
        public List<Attachment> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            var node = (JsonNode) parser.getCodec().readTree(parser);
            if (node.isArray()) {
                var attachments = new ArrayList<Attachment>();
                for (JsonNode jsonNode : node) {
                    if (jsonNode.isObject()) {
                        var data = jsonNode.get("data");
                        if (data.isBinary()) {
                            var attachmentResult = new Attachment();
                            attachmentResult.setData(Base64.getEncoder().encodeToString(data.binaryValue().clone()));
                            attachments.add(attachmentResult);
                        }
                    }
                }
                return attachments;
            }
            return null;
        }
    }
}
