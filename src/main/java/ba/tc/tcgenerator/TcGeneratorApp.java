package ba.tc.tcgenerator;

import akka.Done;
import akka.actor.ActorSystem;
import akka.management.javadsl.AkkaManagement;
import akka.stream.KillSwitch;
import akka.stream.Materializer;
import ba.tc.TopicProducer;
import ba.tc.tcgenerator.TcGenerator;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

public class TcGeneratorApp {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("tc-generator");
        AkkaManagement.get(system).start();

        Config producerConfig = system.settings().config().getConfig("my-producer");
        Logger log = LoggerFactory.getLogger(TcGeneratorApp.class);
        log.info("Starting...");
        TopicProducer.create(system.classicSystem(),producerConfig)
                     .thenAccept(topicProducer -> {
                         TcGenerator tcGenerator = new TcGenerator(system.classicSystem(),topicProducer);
                         KillSwitch killSwitch = tcGenerator.start();
                         Runtime.getRuntime().addShutdownHook(new Thread(){
                             @Override
                             public void run() {
                                 log.info("Stopping...");
                                 AkkaManagement.get(system).stop();
                                 killSwitch.shutdown();
                                 system.terminate();
                             }
                         });
                     });

    }
}
