package ba.tc.tcprocessor;

import akka.actor.ActorSystem;
import akka.management.javadsl.AkkaManagement;
import akka.stream.KillSwitch;
import akka.stream.Materializer;
import ba.tc.TopicProducer;
import ba.tc.tcgenerator.TcGenerator;
import ba.tc.tcgenerator.TcGeneratorApp;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcProcessorApp {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("tc-processor");
        AkkaManagement.get(system).start();
        Materializer materializer = Materializer.createMaterializer(system);
        Config config = system.settings().config();
        Config producerConfig = config.getConfig("my-producer");
        Config consumerConfig = config.getConfig("my-consumer");
        Logger log = LoggerFactory.getLogger(TcProcessorApp.class);
        log.info("Starting...");
        TopicProducer.create(system.classicSystem(),producerConfig)
                     .thenAccept(topicProducer -> {
                         TcProcessor tcProcessor = new TcProcessor(system.classicSystem(),materializer,new TcProcessorBusinessLogicMock(),topicProducer,consumerConfig);
                         tcProcessor.start();
                         Runtime.getRuntime().addShutdownHook(new Thread(){
                             @Override
                             public void run() {
                                 AkkaManagement.get(system).stop();
                                 tcProcessor.shutdown();
                                 system.terminate();
                             }
                         });
                     });

    }
}
