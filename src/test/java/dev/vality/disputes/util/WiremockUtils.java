package dev.vality.disputes.util;

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

@SuppressWarnings({"FileTabCharacter", "LineLength"})
public class WiremockUtils {

    public static void mockS3Attachment() {
        stubFor(WireMock.put(TestUrlPaths.S3_URL + TestUrlPaths.MOCK)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));
    }
}
