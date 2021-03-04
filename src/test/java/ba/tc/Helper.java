package ba.tc;

import lombok.Getter;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Getter
public class Helper {
    private final KafkaContainer kafka;
    public final String kafkaBootstrapServers;

    public Helper() {
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka").withTag("5.4.1")); // contains Kafka 2.4.x
        kafka.start();
        kafkaBootstrapServers = kafka.getBootstrapServers();
    }
    public void stopContainers() {
        kafka.stop();
    }

}
