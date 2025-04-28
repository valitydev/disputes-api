package dev.vality.disputes.util;

import dev.vality.disputes.exception.UnexpectedMimeTypeException;
import org.apache.tika.Tika;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;

public class AttachmentValidator {

    private static final Tika tika = new Tika();

    public static void validateMimeType(byte[] base64Content, String expectedMimeType) {
        MediaType.valueOf(expectedMimeType);
        if (!isValid(base64Content, expectedMimeType)) {
            throw new UnexpectedMimeTypeException();
        }
    }

    private static boolean isValid(byte[] base64Content, String expectedMimeType) {
        try {
            var detectedMimeType = tika.detect(new ByteArrayInputStream(base64Content));
            return expectedMimeType.equalsIgnoreCase(detectedMimeType);
        } catch (Exception e) {
            return false;
        }
    }
}
