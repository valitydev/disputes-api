package dev.vality.disputes.flow;

import dev.vality.damsel.domain.Failure;
import dev.vality.disputes.domain.enums.DisputeStatus;
import dev.vality.disputes.utils.ErrorFormatter;
import dev.vality.woody.api.flow.error.WRuntimeException;

import static dev.vality.disputes.constant.ModerationPrefix.DISPUTES_UNKNOWN_MAPPING;

@SuppressWarnings({"LineLength"})
public class DisputesStepResolver {

    public DisputeStatus resolveNextStep(
            DisputeStatus status, Boolean isDefaultRouteUrl, Failure failure,
            Boolean isAlreadyExistResult, WRuntimeException unexpectedResultMapping, String handleFailedResultErrorMessage,
            Boolean isSuccessDisputeCheckStatusResult, Boolean isPoolingExpired, Boolean isProviderDisputeNotFound,
            Boolean isAdminApproveCall, Boolean isAdminCancelCall, Boolean isAdminBindCall, Boolean isSkipHgCallApproveFlag,
            Boolean isSuccessProviderPaymentStatus) {
        return switch (status) {
            case created -> {
                if (isAdminCancelCall) {
                    yield DisputeStatus.cancelled;
                }
                if (isSuccessProviderPaymentStatus) {
                    yield DisputeStatus.create_adjustment;
                }
                if (handleFailedResultErrorMessage != null) {
                    yield DisputeStatus.failed;
                }
                if (unexpectedResultMapping != null) {
                    yield DisputeStatus.manual_created;
                }
                if (failure != null) {
                    var errorMessage = ErrorFormatter.getErrorMessage(failure);
                    if (errorMessage.startsWith(DISPUTES_UNKNOWN_MAPPING)) {
                        yield DisputeStatus.manual_created;
                    }
                    yield DisputeStatus.failed;
                }
                if (isAlreadyExistResult) {
                    yield DisputeStatus.already_exist_created;
                }
                if (isDefaultRouteUrl) {
                    yield DisputeStatus.manual_pending;
                }
                yield DisputeStatus.pending;
            }
            case pending -> {
                if (isAdminCancelCall) {
                    yield DisputeStatus.cancelled;
                }
                if (isAdminApproveCall && !isSkipHgCallApproveFlag) {
                    yield DisputeStatus.create_adjustment;
                }
                if (isAdminApproveCall) {
                    yield DisputeStatus.succeeded;
                }
                if (handleFailedResultErrorMessage != null) {
                    yield DisputeStatus.failed;
                }
                if (isPoolingExpired) {
                    yield DisputeStatus.manual_pending;
                }
                if (isProviderDisputeNotFound) {
                    yield DisputeStatus.created;
                }
                if (unexpectedResultMapping != null) {
                    yield DisputeStatus.manual_pending;
                }
                if (failure != null) {
                    var errorMessage = ErrorFormatter.getErrorMessage(failure);
                    if (errorMessage.startsWith(DISPUTES_UNKNOWN_MAPPING)) {
                        yield DisputeStatus.manual_pending;
                    }
                    yield DisputeStatus.failed;
                }
                if (!isSuccessDisputeCheckStatusResult) {
                    yield DisputeStatus.pending;
                }
                yield DisputeStatus.create_adjustment;
            }
            case create_adjustment -> {
                if (isAdminCancelCall) {
                    yield DisputeStatus.cancelled;
                }
                if (isAdminApproveCall) {
                    yield DisputeStatus.succeeded;
                }
                if (handleFailedResultErrorMessage != null) {
                    yield DisputeStatus.failed;
                }
                yield DisputeStatus.succeeded;
            }
            case manual_created -> {
                if (isAdminCancelCall) {
                    yield DisputeStatus.cancelled;
                }
                if (isAdminBindCall) {
                    yield DisputeStatus.manual_pending;
                }
                throw new DeadEndFlowException();
            }
            case manual_pending -> {
                if (isAdminCancelCall) {
                    yield DisputeStatus.cancelled;
                }
                if (isAdminApproveCall && !isSkipHgCallApproveFlag) {
                    yield DisputeStatus.create_adjustment;
                }
                if (isAdminApproveCall) {
                    yield DisputeStatus.succeeded;
                }
                throw new DeadEndFlowException();
            }
            case already_exist_created -> {
                if (isAdminCancelCall) {
                    yield DisputeStatus.cancelled;
                }
                if (isAdminBindCall) {
                    yield DisputeStatus.pending;
                }
                throw new DeadEndFlowException();
            }
            case cancelled -> DisputeStatus.cancelled;
            case failed -> DisputeStatus.failed;
            case succeeded -> DisputeStatus.succeeded;
        };
    }

    public static class DeadEndFlowException extends RuntimeException {
    }
}
