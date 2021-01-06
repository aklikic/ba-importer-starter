package ba.tc;

import akka.actor.ActorSystem;
import akka.discovery.Discovery;
import akka.discovery.Lookup;
import akka.kafka.ConsumerSettings;
import akka.kafka.ProducerSettings;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Function;

public class KafkaDiscovery {
    private static Logger log = LoggerFactory.getLogger(KafkaDiscovery.class);
    public static <K,V> Function<ProducerSettings<K,V>, java.util.concurrent.CompletionStage<ProducerSettings<K,V>>> producerBootstrapServers (Config config, ActorSystem system, int defaultPort){
        return settings ->
            Discovery.get(system).discovery().lookup(Lookup.parseSrv(config.getString("service-name")), config.getDuration("resolve-timeout"))
                    .thenApply(resolved-> resolved.addresses()
                                                  .map(target->String.format("%s:%s",target.host(),target.getPort().orElse(defaultPort)))
                                                  .mkString(",")
                    )
                    .thenApply(bootstrapServers->{log.info("producerBootstrapServers for {}: {}",config.getString("service-name"),bootstrapServers);return bootstrapServers;})
                    .thenApply(bootstrapServers->settings.withBootstrapServers(bootstrapServers));

    }

    public static <K,V> Function<ConsumerSettings<K,V>, java.util.concurrent.CompletionStage<ConsumerSettings<K,V>>> consumerBootstrapServers (Config config, ActorSystem system, int defaultPort){
        return settings ->
                Discovery.get(system).discovery().lookup(Lookup.parseSrv(config.getString("service-name")), config.getDuration("resolve-timeout"))
                        .thenApply(resolved-> resolved.addresses()
                                .map(target->String.format("%s:%s",target.host(),target.getPort().orElse(defaultPort)))
                                .mkString(",")
                        )
                        .thenApply(bootstrapServers->{log.info("consumerBootstrapServers for {}: {}",config.getString("service-name"),bootstrapServers);return bootstrapServers;})
                        .thenApply(bootstrapServers->settings.withBootstrapServers(bootstrapServers));

    }
}
