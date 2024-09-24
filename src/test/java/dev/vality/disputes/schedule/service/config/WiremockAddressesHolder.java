package dev.vality.disputes.schedule.service.config;

import dev.vality.disputes.config.WiremockServerPort;
import dev.vality.disputes.util.TestUrlPaths;
import org.springframework.boot.test.context.TestComponent;

@TestComponent
public class WiremockAddressesHolder {

    @WiremockServerPort
    private int wiremockPort;

    public String getDownloadUrl() {
        return getUploadUrl();
    }

    public String getUploadUrl() {
        return String.format("http://127.0.0.1:%s%s%s", wiremockPort, TestUrlPaths.S3_URL, TestUrlPaths.MOCK);
    }
}
