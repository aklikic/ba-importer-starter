package ba.tc;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.stream.Materializer;
import akka.testkit.javadsl.TestKit;
import ba.tc.bundleprocessor.BundleProcessor;
import ba.tc.bundleprocessor.BundleProcessorBusinessLogicMock;
import ba.tc.tcgenerator.TcGenerator;
import ba.tc.tcprocessor.*;
import ba.tc.tcprocessor.sync.ActorBlockingFacilitator;
import com.typesafe.config.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestAll {
    private static Logger log = LoggerFactory.getLogger(TestAll.class);
    private final Helper helper ;

    private final int workParallelism = 5;
    private final int workTimeoutMillis = 2000;
    private final int tcGeneratorFrequencyMillis = workTimeoutMillis / workParallelism;

    private final boolean useActorForSync = true;

    public static void main(String[] args) throws Exception{
        TestAll test = new TestAll();
        test.test();
    }

    public TestAll(){
        helper = new Helper();
        URI bootstrapUri = URI.create(helper.kafkaBootstrapServers);
        System.setProperty("KAFKA_HOST",bootstrapUri.getHost());
        System.setProperty("KAFKA_PORT",bootstrapUri.getPort()+"");
    }

    @Test
    public void test()throws Exception{
        ActorSystem testSystem =
        ActorSystem.create(
                Behaviors.setup(
                        context -> {
                            ActorSystem system = context.getSystem();
                            Materializer materializer = Materializer.createMaterializer(system);
                            Config config = system.settings().config();
                            Config producerConfig = config.getConfig("my-producer");
                            Config consumerConfig = config.getConfig("my-consumer");
                            final ActorRef<ActorBlockingFacilitator.Command> workRouter;
                            if(useActorForSync) {
                                workRouter = ActorBlockingFacilitator.create(context, workParallelism, workTimeoutMillis);
                                log.info("workerROuter: {}", workRouter.path());
                            }else
                                workRouter = null;
                            TopicProducer.create(system.classicSystem(),producerConfig)
                                    .thenAccept(topicProducer -> {
                                        TcGenerator tcGenerator = new TcGenerator(system.classicSystem(),topicProducer,tcGeneratorFrequencyMillis);
                                        tcGenerator.start();

                                        TcProcessor tcProcessor = null;
                                        if(useActorForSync)
                                            tcProcessor = new TcProcessor(system.classicSystem(),materializer,new TcProcessorBusinessLogicMockWithBlockingActor(workRouter,workParallelism,workTimeoutMillis),topicProducer,consumerConfig);
                                        else
                                            tcProcessor = new TcProcessor(system.classicSystem(),materializer,new TcProcessorBusinessLogicMockWithBlockingCf(system,workParallelism,workTimeoutMillis),topicProducer,consumerConfig);


                                        tcProcessor.start();

                                        BundleProcessor bundleProcessor = new BundleProcessor(system.classicSystem(),materializer,consumerConfig,new BundleProcessorBusinessLogicMock(materializer));
                                        bundleProcessor.start();
                                    }).exceptionally(e->{
                                        e.printStackTrace();
                                        return null;
                                    });
                            return Behaviors.empty();
                        }),
                "Test");



        Thread.sleep(150000);
        TestKit.shutdownActorSystem(testSystem.classicSystem());
    }

    @AfterAll
    void afterClass() {
        helper.stopContainers();
    }
}
