package dev.vality.disputes.config;

import dev.vality.disputes.util.TestUrlPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestComponent;

@TestComponent
public class WiremockAddressesHolder {

    @Value("${wiremock.server.baseUrl}")
    private String baseUrl;

    public String getDownloadUrl() {
        return String.format(baseUrl + TestUrlPaths.S3_PATH + TestUrlPaths.MOCK_DOWNLOAD);
    }

    public String getUploadUrl() {
        return String.format(baseUrl + TestUrlPaths.S3_PATH + TestUrlPaths.MOCK_UPLOAD);
    }

    public String getNotificationUrl() {
        return String.format(baseUrl + TestUrlPaths.NOTIFICATION_PATH);
    }
}
