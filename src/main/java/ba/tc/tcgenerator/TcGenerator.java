package ba.tc.tcgenerator;

import akka.Done;
import akka.actor.ActorSystem;
import akka.kafka.javadsl.Producer;
import akka.stream.*;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import ba.tc.Serializers;
import ba.tc.TopicProducer;
import ba.tc.bundleprocessor.BundleProcessorBusinessLogicMock;
import ba.tc.datamodel.TransportContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public class TcGenerator {

    private static Logger log = LoggerFactory.getLogger(TcGenerator.class);
    private Duration frequency = Duration.ofMillis(1000);
    private final TopicProducer topicProducer;
    private final Materializer materializer;
    private final String tcTopic;

    public TcGenerator(ActorSystem system,TopicProducer topicProducer) {
        this.topicProducer = topicProducer;
        this.materializer = Materializer.createMaterializer(system);
        this.tcTopic = system.settings().config().getString("topic.tc");
    }


    private Source<TransportContainer, ?> generatorSource () {
        return Source.tick(frequency, frequency, "")
                     .map(tick -> createNewTc());
    }
    private TransportContainer createNewTc(){
        UUID tcId = UUID.randomUUID();
        //log.info("Generating TC:{}",tcId);
        return TransportContainer.newBuilder()
                .setTcId(tcId.toString())
                .setUri("uri").build();
    }

    public KillSwitch start(){
        log.info("Start");
        Attributes attr = ActorAttributes.withSupervisionStrategy(
                exception -> {
                    exception.printStackTrace();
                        return (Supervision.Directive) Supervision.stop();

                });
        return
        generatorSource().map(tc-> Serializers.tcProducerSerializer.apply(tc,tcTopic))
                         .withAttributes(attr)
                         .viaMat(KillSwitches.single(), Keep.right())
                         .toMat(Producer.plainSink(topicProducer.producerSettings()), Keep.both())
                         .named("tc-generator-stream")
                         .run(materializer)
                         .first();
                         //.runWith(Producer.plainSink(topicProducer.producerSettings()),materializer);
    }

}
