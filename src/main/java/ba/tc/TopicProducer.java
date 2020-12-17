package ba.tc;

import akka.actor.ActorSystem;
import akka.kafka.ProducerSettings;
import akka.kafka.javadsl.DiscoverySupport;
import com.typesafe.config.Config;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.concurrent.CompletionStage;


public class TopicProducer {

    private final ProducerSettings<String, byte[]> producerSettings;
    private final Producer<String, byte[]> producer;

    public TopicProducer(ProducerSettings<String, byte[]> producerSettings, Producer<String, byte[]> producer) {
        this.producerSettings = producerSettings;
        this.producer = producer;
    }


    public static CompletionStage<TopicProducer> create(ActorSystem system, Config producerConfig){

        ProducerSettings<String, byte[]> settings=
                ProducerSettings.create(producerConfig, new StringSerializer(), new ByteArraySerializer())
                                .withEnrichCompletionStage(DiscoverySupport.producerBootstrapServers(producerConfig, system));
        return settings.createKafkaProducerCompletionStage(system.dispatcher())
                       .thenApply(producer->new TopicProducer(settings,producer));
    }

    public ProducerSettings<String, byte[]> producerSettings(){
        return producerSettings.withProducer(producer);
    }


}
