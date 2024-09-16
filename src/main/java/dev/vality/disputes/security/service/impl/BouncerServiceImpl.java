package dev.vality.disputes.security.service.impl;

import dev.vality.bouncer.decisions.ArbiterSrv;
import dev.vality.bouncer.decisions.Resolution;
import dev.vality.disputes.config.properties.BouncerProperties;
import dev.vality.disputes.exception.BouncerException;
import dev.vality.disputes.security.AccessData;
import dev.vality.disputes.security.BouncerContextFactory;
import dev.vality.disputes.security.service.BouncerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({"ParameterName", "LineLength"})
public class BouncerServiceImpl implements BouncerService {

    private final BouncerProperties bouncerProperties;
    private final BouncerContextFactory bouncerContextFactory;
    private final ArbiterSrv.Iface bouncerClient;

    @Override
    public Resolution getResolution(AccessData accessData) {
        log.debug("Check access with bouncer context: {}{}", accessData.getInvoice().getInvoice().getId(), accessData.getPayment().getPayment().getId());
        var context = bouncerContextFactory.buildContext(accessData);
        log.debug("Built thrift context: {}{}", accessData.getInvoice().getInvoice().getId(), accessData.getPayment().getPayment().getId());
        try {
            var judge = bouncerClient.judge(bouncerProperties.getRuleSetId(), context);
            log.debug("Have judge: {}", judge);
            var resolution = judge.getResolution();
            log.debug("Resolution: {}", resolution);
            return resolution;
        } catch (TException e) {
            throw new BouncerException("Error while call bouncer", e);
        }
    }
}
