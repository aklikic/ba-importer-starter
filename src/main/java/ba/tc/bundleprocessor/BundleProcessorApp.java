package ba.tc.bundleprocessor;

import akka.actor.ActorSystem;
import akka.management.javadsl.AkkaManagement;
import akka.stream.Materializer;
import ba.tc.TopicProducer;
import ba.tc.tcgenerator.TcGeneratorApp;
import ba.tc.tcprocessor.TcProcessor;
import ba.tc.tcprocessor.TcProcessorBusinessLogicMock;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BundleProcessorApp {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("bundle-processor");
        AkkaManagement.get(system).start();
        Materializer materializer = Materializer.createMaterializer(system);
        Config config = system.settings().config();
        Config producerConfig = config.getConfig("my-producer");
        Config consumerConfig = config.getConfig("my-consumer");
        Logger log = LoggerFactory.getLogger(BundleProcessorApp.class);
        log.info("Starting...");
        TopicProducer.create(system.classicSystem(),producerConfig)
                     .thenAccept(topicProducer -> {
                         BundleProcessor bundleProcessor = new BundleProcessor(system.classicSystem(),materializer,consumerConfig,new BundleProcessorBusinessLogicMock());
                         bundleProcessor.start();
                         Runtime.getRuntime().addShutdownHook(new Thread(){
                             @Override
                             public void run() {
                                 AkkaManagement.get(system).stop();
                                 bundleProcessor.shutdown();
                                 system.terminate();
                             }
                         });
                     });

    }
}
