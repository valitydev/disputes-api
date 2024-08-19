package dev.vality.disputes.security;

import dev.vality.bouncer.context.v1.ContextFragment;
import dev.vality.bouncer.context.v1.ContextPaymentProcessing;
import dev.vality.bouncer.context.v1.Deployment;
import dev.vality.bouncer.context.v1.Environment;
import dev.vality.bouncer.decisions.Context;
import dev.vality.disputes.config.properties.BouncerProperties;
import dev.vality.disputes.converter.ContextFragmentV1ToContextFragmentConverter;
import dev.vality.disputes.converter.PaymentProcessingInvoiceToBouncerInvoiceConverter;
import dev.vality.disputes.converter.PaymentProcessingInvoiceToCommonApiOperationConverter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@RequiredArgsConstructor
@Component
@Slf4j
public class BouncerContextFactory {

    private final BouncerProperties bouncerProperties;
    private final PaymentProcessingInvoiceToBouncerInvoiceConverter invoiceToPaymentProcConverter;
    private final PaymentProcessingInvoiceToCommonApiOperationConverter invoiceToCapiOpConverter;
    private final ContextFragmentV1ToContextFragmentConverter contextFragmentV1ToContextFragmentConverter;

    @SneakyThrows
    public Context buildContext(AccessData accessData) {
        var contextFragmentV1 = buildCapiContextFragment(accessData);
        var capiContextFragment = contextFragmentV1ToContextFragmentConverter.convertContextFragment(contextFragmentV1);
        var tokenKeeperFragmentContent = accessData.getAuthData().getContext().getContent();
        var tokenKeeperContextFragment =
                contextFragmentV1ToContextFragmentConverter.convertContextFragment(tokenKeeperFragmentContent);
        var context = new Context();
        context.putToFragments(ContextFragmentName.TOKEN_KEEPER, tokenKeeperContextFragment);
        context.putToFragments(ContextFragmentName.CAPI, capiContextFragment);
        return context;
    }

    private ContextFragment buildCapiContextFragment(AccessData accessData) {
        var env = buildEnvironment();
        var contextPaymentProcessing = buildPaymentProcessingContext(accessData);
        ContextFragment fragment = new ContextFragment();
        return fragment
                .setCapi(invoiceToCapiOpConverter.convert(accessData.getInvoice()))
                .setEnv(env)
                .setPaymentProcessing(contextPaymentProcessing);
    }

    private Environment buildEnvironment() {
        var deployment = new Deployment()
                .setId(bouncerProperties.getDeploymentId());
        return new Environment()
                .setDeployment(deployment)
                .setNow(Instant.now().toString());
    }

    private ContextPaymentProcessing buildPaymentProcessingContext(AccessData accessData) {
        return new ContextPaymentProcessing()
                .setInvoice(invoiceToPaymentProcConverter.convert(accessData.getInvoice()));
    }
}
