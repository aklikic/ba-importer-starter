package ba.tc;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.discovery.Discovery;
import akka.discovery.Lookup;
import akka.discovery.ServiceDiscovery;
import akka.kafka.ConsumerSettings;
import akka.kafka.ProducerMessage;
import akka.kafka.ProducerSettings;
import akka.stream.javadsl.Flow;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class KafkaDiscovery {
    private static Logger log = LoggerFactory.getLogger(KafkaDiscovery.class);
    public static <K,V> Function<ProducerSettings<K,V>, java.util.concurrent.CompletionStage<ProducerSettings<K,V>>> producerBootstrapServers (Config config, ActorSystem system, int defaultPort){
        return settings ->
            Discovery.get(system).discovery().lookup(getLookup(config), config.getDuration("resolve-timeout"))
                    .thenApply(resolved -> resolved.addresses()
                            .map(target -> String.format("%s:%s", target.host(), target.getPort().orElse(defaultPort)))
                            .mkString(",")
                    ).thenApply(bootstrapServers -> settings.withBootstrapServers(bootstrapServers));

    }

    public static <K,V> Function<ConsumerSettings<K,V>, java.util.concurrent.CompletionStage<ConsumerSettings<K,V>>> consumerBootstrapServers (Config config, ActorSystem system, int defaultPort){
        return settings ->
                Discovery.get(system).discovery().lookup(getLookup(config), config.getDuration("resolve-timeout"))
                        .thenApply(resolved-> resolved.addresses()
                                .map(target->String.format("%s:%s",target.host(),target.getPort().orElse(defaultPort)))
                                .mkString(",")
                        )
                        .thenApply(bootstrapServers->{log.info("consumerBootstrapServers for {}: {}",config.getString("service-name"),bootstrapServers);return bootstrapServers;})
                        .thenApply(bootstrapServers->settings.withBootstrapServers(bootstrapServers));

    }

    private static Lookup getLookup(Config config){
        if(Lookup.isValidSrv(config.getString("service-name")))
            return Lookup.parseSrv(config.getString("service-name"));
        else
            return Lookup.apply(config.getString("service-name"));
    }


}
