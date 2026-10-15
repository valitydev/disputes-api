package dev.vality.disputes.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValuesGenerator {

    public static String generateId() {
        return UUID.randomUUID().toString();
    }
}
