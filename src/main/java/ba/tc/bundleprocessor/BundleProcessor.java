package ba.tc.bundleprocessor;

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
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;
import ba.tc.tcgenerator.TcGenerator;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class BundleProcessor {
    private static Logger log = LoggerFactory.getLogger(BundleProcessor.class);
    private final TopicConsumer<Bundle> bundleConsumer;
    private final BundleProcessorBusinessLogic businessLogic;
    private final RestartSettings restartSettings = RestartSettings.create(java.time.Duration.ofSeconds(3),java.time.Duration.ofSeconds(30),0.2);
    private final CommitterSettings committerSettings;
    private final Materializer materializer;
    private final AtomicReference<Consumer.Control> control = new AtomicReference<>(Consumer.createNoopControl());
    private CompletionStage<Done> streamCompletion;

    public BundleProcessor(ActorSystem system, Materializer materializer, Config consumerConfig, BundleProcessorBusinessLogic businessLogic) {
        this.businessLogic=businessLogic;
        this.materializer=materializer;
        String bundleTopic = system.settings().config().getString("topic.bundle");
        String consumerGroupId = "bundle-processor";
        this.bundleConsumer = new TopicConsumer<>(system, consumerConfig,bundleTopic, consumerGroupId, Serializers.bundleDeSerializer, Serializers.resumeOnDeSerializeException());
        this.committerSettings = CommitterSettings.create(system);
    }

    public void start(){
        log.info("Start");
        Source<ConsumerMessage.CommittableOffset, Consumer.Control> source =
          bundleConsumer.getConsumerSource()
                        .mapMaterializedValue(c->{
                            control.set(c);
                            return c;
                        })
                        .via(businessLogic.getFlow())
                        .asSource()
                        .map(m -> m.second());

        this.streamCompletion =
        RestartSource.onFailuresWithBackoff(restartSettings,() -> source)
                .toMat(Committer.sink(committerSettings), Keep.both())
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
