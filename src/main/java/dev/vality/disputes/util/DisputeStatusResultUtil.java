package dev.vality.disputes.util;

import dev.vality.disputes.provider.DisputeStatusResult;
import dev.vality.disputes.provider.DisputeStatusSuccessResult;

import java.util.Optional;

public class DisputeStatusResultUtil {

    public static DisputeStatusResult getDisputeStatusResult(Long changedAmount) {
        return Optional.ofNullable(changedAmount)
                .map(amount -> DisputeStatusResult.statusSuccess(
                        new DisputeStatusSuccessResult().setChangedAmount(amount)))
                .orElse(DisputeStatusResult.statusSuccess(new DisputeStatusSuccessResult()));
    }
}
