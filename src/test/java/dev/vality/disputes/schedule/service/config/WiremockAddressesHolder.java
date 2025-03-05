package dev.vality.disputes.schedule.service.config;

import dev.vality.disputes.config.WiremockServerPort;
import dev.vality.disputes.util.TestUrlPaths;
import org.springframework.boot.test.context.TestComponent;

@TestComponent
public class WiremockAddressesHolder {

    @WiremockServerPort
    private int wiremockPort;

    public String getDownloadUrl() {
        return String.format("http://127.0.0.1:%s%s%s", wiremockPort, TestUrlPaths.S3_PATH, TestUrlPaths.MOCK_DOWNLOAD);
    }

    public String getUploadUrl() {
        return String.format("http://127.0.0.1:%s%s%s", wiremockPort, TestUrlPaths.S3_PATH, TestUrlPaths.MOCK_UPLOAD);
    }

    public String getNotificationUrl() {
        return String.format("http://127.0.0.1:%s%s", wiremockPort, TestUrlPaths.NOTIFICATION_PATH);
    }
}
