package dev.vality.disputes.service.external.impl;

import dev.vality.disputes.admin.*;
import dev.vality.disputes.exception.DisputesTgBotException;
import dev.vality.disputes.provider.DisputeCreatedResult;
import dev.vality.disputes.provider.DisputeParams;
import dev.vality.disputes.provider.ProviderDisputesServiceSrv;
import dev.vality.disputes.service.external.DisputesTgBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"LineLength"})
public class DisputesTgBotServiceImpl implements DisputesTgBotService {

    public final ProviderDisputesServiceSrv.Iface providerDisputesTgBotClient;
    public final AdminCallbackServiceSrv.Iface adminCallbackDisputesTgBotClient;

    @Override
    public DisputeCreatedResult createDispute(DisputeParams disputeParams) {
        try {
            log.debug("Trying to call providerDisputesTgBotClient.createDispute() {} {}", disputeParams.getDisputeId(), disputeParams.getTransactionContext().getInvoiceId());
            var invoice = providerDisputesTgBotClient.createDispute(disputeParams);
            log.debug("providerDisputesTgBotClient.createDispute() has been called {} {}", disputeParams.getDisputeId(), disputeParams.getTransactionContext().getInvoiceId());
            return invoice;
        } catch (TException e) {
            throw new DisputesTgBotException(String.format("Failed to call providerDisputesTgBotClient.createDispute() with id: %s, %s", disputeParams.getDisputeId(), disputeParams.getTransactionContext().getInvoiceId()), e);
        }
    }

    @Override
    public void sendDisputeAlreadyCreated(DisputeAlreadyCreated disputeAlreadyCreated) {
        try {
            log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputeAlreadyCreated() {}", disputeAlreadyCreated.getId());
            adminCallbackDisputesTgBotClient.notify(
                    new NotificationParamsRequest(List.of(Notification.disputeAlreadyCreated(disputeAlreadyCreated))));
            log.debug("adminCallbackDisputesTgBotClient.sendDisputeAlreadyCreated() has been called {}", disputeAlreadyCreated.getId());
        } catch (TException e) {
            throw new DisputesTgBotException(String.format("Failed to call adminCallbackDisputesTgBotClient.sendDisputeAlreadyCreated() with id: %s", disputeAlreadyCreated.getId()), e);
        }
    }

    @Override
    public void sendDisputePoolingExpired(DisputePoolingExpired disputePoolingExpired) {
        try {
            log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputePoolingExpired() {}", disputePoolingExpired.getId());
            adminCallbackDisputesTgBotClient.notify(
                    new NotificationParamsRequest(List.of(Notification.disputePoolingExpired(disputePoolingExpired))));
            log.debug("adminCallbackDisputesTgBotClient.sendDisputePoolingExpired() has been called {}", disputePoolingExpired.getId());
        } catch (TException e) {
            throw new DisputesTgBotException(String.format("Failed to call adminCallbackDisputesTgBotClient.sendDisputePoolingExpired() with id: %s", disputePoolingExpired.getId()), e);
        }
    }

    @Override
    public void sendDisputeReadyForCreateAdjustment(List<DisputeReadyForCreateAdjustment> disputeReadyForCreateAdjustments) {
        var ids = disputeReadyForCreateAdjustments.stream()
                .map(DisputeReadyForCreateAdjustment::getId)
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        try {
            log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputeReadyForCreateAdjustment() {}", ids);
            var notifications = disputeReadyForCreateAdjustments.stream()
                    .map(Notification::disputeReadyForCreateAdjustment)
                    .collect(Collectors.toList());
            adminCallbackDisputesTgBotClient.notify(new NotificationParamsRequest(notifications));
            log.debug("adminCallbackDisputesTgBotClient.sendDisputeReadyForCreateAdjustment() has been called {}", ids);
        } catch (TException e) {
            throw new DisputesTgBotException(String.format("Failed to call adminCallbackDisputesTgBotClient.sendDisputeReadyForCreateAdjustment() with id: %s", ids), e);
        }
    }

    @Override
    public void sendDisputeFailedReviewRequired(DisputeFailedReviewRequired disputeFailedReviewRequired) {
        try {
            log.debug("Trying to call adminCallbackDisputesTgBotClient.sendDisputeFailedReviewRequired() {}", disputeFailedReviewRequired.getId());
            adminCallbackDisputesTgBotClient.notify(
                    new NotificationParamsRequest(List.of(Notification.disputeFailedReviewRequired(disputeFailedReviewRequired))));
            log.debug("adminCallbackDisputesTgBotClient.sendDisputeFailedReviewRequired() has been called {}", disputeFailedReviewRequired.getId());
        } catch (TException e) {
            throw new DisputesTgBotException(String.format("Failed to call adminCallbackDisputesTgBotClient.sendDisputeFailedReviewRequired() with id: %s", disputeFailedReviewRequired.getId()), e);
        }
    }
}
