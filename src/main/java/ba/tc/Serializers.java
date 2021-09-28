package ba.tc;

import akka.NotUsed;
import akka.http.javadsl.model.HttpEntity;
import akka.japi.Pair;
import akka.kafka.ConsumerMessage;
import akka.kafka.ProducerMessage;
import akka.kafka.javadsl.Producer;
import akka.stream.ActorAttributes;
import akka.stream.Attributes;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.FlowWithContext;
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;
import org.apache.avro.io.*;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Serializers {

    public static Function<byte[], TransportContainer> tcDeSerializer = (data)-> {
        DatumReader<TransportContainer> reader
                = new SpecificDatumReader<>(TransportContainer.class);
        Decoder decoder = null;
        try {
            decoder = DecoderFactory.get().jsonDecoder(
                    TransportContainer.getClassSchema(), new String(data));
            return reader.read(null, decoder);
        } catch (IOException e) {
            //throw new RuntimeException(e);
            return null;
        }

    };
    public static Function<TransportContainer,byte[]> tcSerializer = (tc)-> {
        DatumWriter<TransportContainer> writer = new SpecificDatumWriter<>(
                TransportContainer.class);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encoder jsonEncoder = null;
        try {
            jsonEncoder = EncoderFactory.get().jsonEncoder(
                    TransportContainer.getClassSchema(), stream);
            writer.write(tc, jsonEncoder);
            jsonEncoder.flush();
           return stream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    };
    public static Function<byte[], Bundle> bundleDeSerializer = (data)-> {
        DatumReader<Bundle> reader
                = new SpecificDatumReader<>(Bundle.class);
        Decoder decoder = null;
        try {
            decoder = DecoderFactory.get().jsonDecoder(
                    Bundle.getClassSchema(), new String(data));
            return reader.read(null, decoder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    public static Attributes resumeOnDeSerializeException() {
        return
        ActorAttributes.withSupervisionStrategy(
                exception -> {
                    if (exception.getCause() instanceof IOException) {
                        return (Supervision.Directive) Supervision.resume();
                    } else {
                        return (Supervision.Directive) Supervision.stop();
                    }
                });
    }

    public static BiFunction<TransportContainer, String, ProducerRecord<String,byte[]>> tcProducerSerializer = (tc,topic) -> new ProducerRecord(topic,tc.getTcId().toString(),tcSerializer.apply(tc));
    public static BiFunction<TransportContainer, String, ProducerMessage.Envelope<String,byte[],Integer>> tcProducerEnvelopeSerializer = (tc,topic) -> ProducerMessage.single(new ProducerRecord(topic,tc.getTcId().toString(),tcSerializer.apply(tc)));


    public static Flow<Pair<List<Bundle>, ConsumerMessage.CommittableOffset>, ProducerMessage.Envelope<String,byte[], ConsumerMessage.CommittableOffset>, ?> getBundleSerializeFlow(String topic) {
        return
        Flow.<Pair<List<Bundle>, ConsumerMessage.CommittableOffset>>create()
                        .map(bundles -> {
                            List<ProducerRecord<String, byte[]>> records = bundles.first().stream()
                                    //bundleId is used for partitioning
                                    .map(bundle -> new ProducerRecord<String, byte[]>(topic,bundle.getBundleId().toString(), serializeBundle(bundle)))
                                    .collect(Collectors.toList());
                            return ProducerMessage.multi(records, bundles.second());
                        });
    }

    private static byte [] serializeBundle(Bundle bundle){
        DatumWriter<Bundle> writer = new SpecificDatumWriter<>(
                Bundle.class);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encoder jsonEncoder = null;
        try {
            jsonEncoder = EncoderFactory.get().jsonEncoder(
                    Bundle.getClassSchema(), stream);
            writer.write(bundle, jsonEncoder);
            jsonEncoder.flush();
            return stream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
