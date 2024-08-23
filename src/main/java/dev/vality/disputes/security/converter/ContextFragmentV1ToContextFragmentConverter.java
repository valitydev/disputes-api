package dev.vality.disputes.security.converter;

import dev.vality.bouncer.ctx.ContextFragment;
import dev.vality.bouncer.ctx.ContextFragmentType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.transport.TTransportException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ContextFragmentV1ToContextFragmentConverter {

    private final TSerializer thriftSerializer = new TSerializer();

    public ContextFragmentV1ToContextFragmentConverter() throws TTransportException {
    }

    @SneakyThrows
    public ContextFragment convertContextFragment(dev.vality.bouncer.context.v1.ContextFragment v1Context) {
        try {
            return convertContextFragment(thriftSerializer.serialize(v1Context));
        } catch (TException e) {
            log.error("Error during ContextFragmentV1 serialization: ", e);
            throw e;
        }
    }

    public ContextFragment convertContextFragment(byte[] v1ContextContent) {
        return new ContextFragment()
                .setType(ContextFragmentType.v1_thrift_binary)
                .setContent(v1ContextContent);
    }
}
