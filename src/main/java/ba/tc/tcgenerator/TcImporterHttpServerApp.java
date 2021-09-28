package ba.tc.tcgenerator;

import akka.actor.ActorSystem;
import akka.management.javadsl.AkkaManagement;
import akka.stream.KillSwitch;
import ba.tc.TopicProducer;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcImporterHttpServerApp {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("tc-generator");
        AkkaManagement.get(system).start();

        Config producerConfig = system.settings().config().getConfig("my-producer");
        Logger log = LoggerFactory.getLogger(TcImporterHttpServerApp.class);
        log.info("Starting...");
        TopicProducer.create(system.classicSystem(),producerConfig)
                     .thenAccept(topicProducer -> {
                         TcGenerator tcGenerator = new TcGenerator(system.classicSystem(),topicProducer,1000);
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
