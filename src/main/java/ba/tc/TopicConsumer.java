package ba.tc;

import akka.actor.ActorSystem;
import akka.kafka.ConsumerMessage;
import akka.kafka.ConsumerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Consumer;
import akka.kafka.javadsl.DiscoverySupport;
import akka.stream.Attributes;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceWithContext;
import com.typesafe.config.Config;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.function.BiFunction;
import java.util.function.Function;

public class TopicConsumer<T> {

    private final ActorSystem system;
    private final Config consumerConfig;
    private final String topicName;
    private final String groupId;
    private final Function<byte[],T> serializer;
    private final Attributes resumeOnSerializeException;
    private final ConsumerSettings<String, byte[]> consumerSettings;

    public TopicConsumer(ActorSystem system,Config consumerConfig, String topicName, String groupId, Function<byte[], T> serializer, Attributes resumeOnSerializeException) {
        this.system=system;
        this.consumerConfig = consumerConfig;
        this.topicName = topicName;
        this.groupId=groupId;
        this.serializer = serializer;
        this.resumeOnSerializeException = resumeOnSerializeException;
        this.consumerSettings = ConsumerSettings.create(consumerConfig, new StringDeserializer(), new ByteArrayDeserializer())
                                                .withGroupId(groupId)
                                                .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                                                //.withEnrichCompletionStage(DiscoverySupport.consumerBootstrapServers(consumerConfig, system));
                                                .withEnrichCompletionStage(KafkaDiscovery.consumerBootstrapServers(consumerConfig, system, 9092));
    }

    public SourceWithContext<T, ConsumerMessage.CommittableOffset,  Consumer.Control> getConsumerSource() {
        return Consumer.committableSource(consumerSettings, Subscriptions.topics(topicName))
                .asSourceWithContext(msg->msg.committableOffset())
                .map(msg -> serializer.apply(msg.record().value()))
                .withAttributes(resumeOnSerializeException);

    }
}
