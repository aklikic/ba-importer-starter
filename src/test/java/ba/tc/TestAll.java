package ba.tc;

import akka.actor.typed.ActorSystem;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.kafka.testkit.javadsl.TestcontainersKafkaTest;
import akka.stream.Materializer;
import akka.testkit.javadsl.TestKit;
import ba.tc.bundleprocessor.BundleProcessor;
import ba.tc.bundleprocessor.BundleProcessorBusinessLogicMock;
import ba.tc.tcgenerator.TcGenerator;
import ba.tc.tcprocessor.TcProcessor;
import ba.tc.tcprocessor.TcProcessorBusinessLogic;
import ba.tc.tcprocessor.TcProcessorBusinessLogicMock;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestAll {
    private static Logger log = LoggerFactory.getLogger(TestAll.class);
    private final ActorTestKit testKit;
    private final ActorSystem system;
    private final Materializer materializer;
    private final Helper helper ;
    private final Config config ;

    public static void main(String[] args) throws Exception{
        TestAll test = new TestAll();
        test.test();
    }

    public TestAll(){
        helper = new Helper();
        URI bootstrapUri = URI.create(helper.kafkaBootstrapServers);
        //log.debug("Kafka: {}",bootstrapUri);
        System.setProperty("KAFKA_HOST",bootstrapUri.getHost());
        System.setProperty("KAFKA_PORT",bootstrapUri.getPort()+"");
        testKit = ActorTestKit.create();
        system = testKit.system();
        materializer = Materializer.createMaterializer(system);
        config = system.settings().config();
    }

    @Test
    public void test()throws Exception{
        Config producerConfig = config.getConfig("my-producer");
        Config consumerConfig = config.getConfig("my-consumer");
        TopicProducer.create(system.classicSystem(),producerConfig)
                     .thenAccept(topicProducer -> {
                         TcGenerator tcGenerator = new TcGenerator(system.classicSystem(),topicProducer);
                         tcGenerator.start();

                         TcProcessor tcProcessor = new TcProcessor(system.classicSystem(),materializer,new TcProcessorBusinessLogicMock(),topicProducer,consumerConfig);
                         tcProcessor.start();

                         BundleProcessor bundleProcessor = new BundleProcessor(system.classicSystem(),materializer,consumerConfig,new BundleProcessorBusinessLogicMock(materializer));
                         bundleProcessor.start();
                     });


        Thread.sleep(150000);

    }

    @AfterAll
    void afterClass() {
        helper.stopContainers();
        TestKit.shutdownActorSystem(system.classicSystem());
    }
}
