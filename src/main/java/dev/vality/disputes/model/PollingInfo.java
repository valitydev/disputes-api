package dev.vality.disputes.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PollingInfo {

    private Instant startDateTimePolling;
    private Instant maxDateTimePolling;

}
