package ba.tc;

import akka.actor.ActorSystem;
import akka.kafka.ProducerSettings;
import akka.kafka.javadsl.DiscoverySupport;
import ba.tc.tcprocessor.TcProcessorBusinessLogicMock;
import com.typesafe.config.Config;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;


public class TopicProducer {

    private static Logger log = LoggerFactory.getLogger(TopicProducer.class);
    private final ProducerSettings<String, byte[]> producerSettings;
    private final Producer<String, byte[]> producer;

    public TopicProducer(ProducerSettings<String, byte[]> producerSettings, Producer<String, byte[]> producer) {
        log.info("DONE!");
        this.producerSettings = producerSettings;
        this.producer = producer;
    }


    public static CompletionStage<TopicProducer> create(ActorSystem system, Config producerConfig){
        log.info("Create...");
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
