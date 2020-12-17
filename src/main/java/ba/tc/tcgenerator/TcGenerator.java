package ba.tc.tcgenerator;

import akka.actor.ActorSystem;
import akka.kafka.javadsl.Producer;
import akka.stream.ActorAttributes;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.Supervision;
import akka.stream.javadsl.Source;
import ba.tc.Serializers;
import ba.tc.TopicProducer;
import ba.tc.datamodel.TransportContainer;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

public class TcGenerator {

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
        System.out.println("Generating TC:"+tcId);
        return TransportContainer.newBuilder()
                .setTcId(tcId.toString())
                .setUri("uri").build();
    }

    public void start(){
        Attributes attr = ActorAttributes.withSupervisionStrategy(
                exception -> {
                    exception.printStackTrace();
                        return (Supervision.Directive) Supervision.stop();

                });
        generatorSource().map(tc-> Serializers.tcSerializer.apply(tc,tcTopic))
                         .withAttributes(attr)
                         .runWith(Producer.plainSink(topicProducer.producerSettings()),materializer);
    }

}
