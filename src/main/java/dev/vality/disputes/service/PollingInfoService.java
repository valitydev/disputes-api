package dev.vality.disputes.service;

import dev.vality.disputes.exception.CascadeTimeoutPoolingException;
import dev.vality.disputes.model.ContextPaymentDto;
import dev.vality.disputes.model.PollingInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PollingInfoService {

    @Value("${cascade.max-time-polling-sec}")
    private int maxTimePollingSec;

    public void initPollingInfo(ContextPaymentDto paymentInfoDto) {
        if (paymentInfoDto.getCascadePollingInfo() == null) {
            var pollingInfo = PollingInfo.builder()
                    .startDateTimePolling(Instant.now())
                    .build();
            pollingInfo.setMaxDateTimePolling(pollingInfo.getStartDateTimePolling()
                    .plus(maxTimePollingSec, ChronoUnit.SECONDS));
            paymentInfoDto.setCascadePollingInfo(pollingInfo);
        }
    }

    public void isDeadline(ContextPaymentDto paymentInfoDto) {
        if (Instant.now().isAfter(paymentInfoDto.getCascadePollingInfo().getMaxDateTimePolling())) {
            throw new CascadeTimeoutPoolingException();
        }
    }
}
