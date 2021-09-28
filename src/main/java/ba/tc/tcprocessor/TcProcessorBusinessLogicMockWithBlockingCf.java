package ba.tc.tcprocessor;

import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.DispatcherSelector;
import akka.kafka.ConsumerMessage;
import akka.stream.javadsl.FlowWithContext;
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;
import ba.tc.tcprocessor.sync.BlockingMockLogic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class TcProcessorBusinessLogicMockWithBlockingCf implements  TcProcessorBusinessLogic{
    private static Logger log = LoggerFactory.getLogger(TcProcessorBusinessLogicMockWithBlockingCf.class);
    private final Integer parallelism;
    private final Integer workTimeoutInMilis;
    //private final Executor executor = Executors.newFixedThreadPool(parallelism);
    private final Executor executor;
    public TcProcessorBusinessLogicMockWithBlockingCf(ActorSystem system, int parallelism, int workTimeoutInMilis){
        this.parallelism = parallelism;
        this.workTimeoutInMilis = workTimeoutInMilis;
        executor = system.dispatchers().lookup(DispatcherSelector.blocking());
        //or you can configure custom dispatcher also: https://doc.akka.io/docs/akka/current/typed/dispatchers.html
    }


    public FlowWithContext<TransportContainer,  ConsumerMessage.CommittableOffset, List<Bundle>,  ConsumerMessage.CommittableOffset, NotUsed> getFlow() {
        return FlowWithContext.<TransportContainer, ConsumerMessage.CommittableOffset>create()
                            .map(tc->{
                                log.info("Processing TC: {}" ,tc.getTcId());
                                return tc;
                            })
                             .mapAsync(parallelism,tc -> {
                                 //log.info("Processing TC: {}" ,tc.getTcId());
                                return bundlesFromTcSync(tc);
                             });
    }

    private CompletionStage<List<Bundle>> bundlesFromTcSync(TransportContainer tc){
        return CompletableFuture.supplyAsync(()->{return BlockingMockLogic.getBundlesFromTcSync(tc,workTimeoutInMilis);},executor)
                                .orTimeout(workTimeoutInMilis, TimeUnit.SECONDS)
                                .exceptionally(ex->{
                                    if(ex instanceof TimeoutException){
                                        //TODO error handling, in this case only skip is done
                                        return new ArrayList<>();
                                    }
                                    throw (RuntimeException)ex;
                                });
    }

}
