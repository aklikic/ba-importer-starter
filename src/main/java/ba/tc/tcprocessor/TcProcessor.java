package ba.tc.tcprocessor;

import akka.Done;
import akka.actor.ActorSystem;
import akka.kafka.CommitterSettings;
import akka.kafka.ConsumerMessage;
import akka.kafka.javadsl.Committer;
import akka.kafka.javadsl.Consumer;
import akka.kafka.javadsl.Producer;
import akka.stream.Materializer;
import akka.stream.RestartSettings;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Source;
import ba.tc.Serializers;
import ba.tc.TopicConsumer;
import ba.tc.TopicProducer;
import ba.tc.datamodel.TransportContainer;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class TcProcessor {

    private static Logger log = LoggerFactory.getLogger(TcProcessor.class);
    private final TopicConsumer<TransportContainer> tcConsumer;
    private final TopicProducer topicProducer;
    private final String tcTopic;
    private final String bundleTopic;
    private final TcProcessorBusinessLogic businessLogic;
    private final Materializer materializer;
    private final CommitterSettings committerSettings;
    private final AtomicReference<Consumer.Control> control = new AtomicReference<>(Consumer.createNoopControl());
    private CompletionStage<Done> streamCompletion;

    private final RestartSettings restartSettings = RestartSettings.create(java.time.Duration.ofSeconds(3),java.time.Duration.ofSeconds(30),0.2);
    public TcProcessor(ActorSystem system, Materializer materializer, TcProcessorBusinessLogic businessLogic, TopicProducer topicProducer, Config consumerConfig) {
        this.topicProducer = topicProducer;
        this.materializer = materializer;
        this.tcTopic = system.settings().config().getString("topic.tc");
        this.bundleTopic = system.settings().config().getString("topic.bundle");
        this.businessLogic = businessLogic;
        String consumerGroupId = "tc-processor";
        tcConsumer = new TopicConsumer<TransportContainer>(system, consumerConfig, tcTopic, consumerGroupId, Serializers.tcDeSerializer, Serializers.resumeOnDeSerializeException());
        this.committerSettings = CommitterSettings.create(system);
    }

    public void start(){
        log.info("Start");
        Source<ConsumerMessage.CommittableOffset,Consumer.Control> source =
        tcConsumer.getConsumerSource()
                .mapMaterializedValue(c->{
                    control.set(c);
                    return c;
                })
                .via(businessLogic.getFlow())
                .asSource()
                .via(Serializers.getBundleSerializeFlow(bundleTopic))
                .via(Producer.flexiFlow(topicProducer.producerSettings()))
                .map(m -> m.passThrough());

        this.streamCompletion =
        RestartSource.onFailuresWithBackoff(restartSettings,() -> source)
                     .toMat(Committer.sink(committerSettings),Keep.both())
                     .mapMaterializedValue(pair->
                             //Consumer.createDrainingControl(control.get(), pair.second())
                             pair.second()
                     ).run(materializer);
    }

    public void shutdown(){
        if(streamCompletion != null)
            control.get().drainAndShutdown(streamCompletion, Executors.newCachedThreadPool());
    }

}
