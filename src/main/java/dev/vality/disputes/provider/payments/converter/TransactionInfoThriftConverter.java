package dev.vality.disputes.provider.payments.converter;

import dev.vality.damsel.domain.TransactionInfo;
import lombok.SneakyThrows;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TSerializer;
import org.springframework.stereotype.Component;

@Component
public class TransactionInfoThriftConverter {

    @SneakyThrows
    public byte[] serialize(TransactionInfo transactionInfo) {
        return new TSerializer().serialize(transactionInfo);
    }

    @SneakyThrows
    public TransactionInfo deserialize(byte[] transactionInfo) {
        var result = new TransactionInfo();
        new TDeserializer().deserialize(result, transactionInfo);
        return result;
    }
}
