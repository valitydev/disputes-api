package dev.vality.disputes.schedule.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ProviderData {

    private Map<String, String> options;
    private String defaultProviderUrl;
    private String routeUrl;

}
