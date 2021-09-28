package ba.tc.tcprocessor;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.japi.Pair;
import akka.kafka.ConsumerMessage;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.FlowWithContext;
import akka.stream.typed.javadsl.ActorFlow;
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;
import ba.tc.tcprocessor.sync.ActorBlockingFacilitator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

public class TcProcessorBusinessLogicMockWithBlockingActor implements  TcProcessorBusinessLogic{
    private static Logger log = LoggerFactory.getLogger(TcProcessorBusinessLogicMockWithBlockingActor.class);
    private final int parallelism;
    private final Duration timeout;
    private final ActorRef<ActorBlockingFacilitator.Command> workerRouter;
    private final Flow<Pair<TransportContainer,ConsumerMessage.CommittableOffset>,Pair<List<Bundle>,ConsumerMessage.CommittableOffset>,?> syncFlow;

    public TcProcessorBusinessLogicMockWithBlockingActor(ActorRef<ActorBlockingFacilitator.Command> workerRouter, int parallelism, int workTimeoutSec){
        this.workerRouter = workerRouter;
        this.parallelism = parallelism;
        this.timeout = Duration.ofSeconds(workTimeoutSec);
        syncFlow = ActorFlow.ask(parallelism,workerRouter,timeout, ActorBlockingFacilitator.Request::new)
                            .map(ActorBlockingFacilitator.Response::getBundles);
    }

    public FlowWithContext<TransportContainer,  ConsumerMessage.CommittableOffset, List<Bundle>,  ConsumerMessage.CommittableOffset, NotUsed> getFlow() {

        return FlowWithContext.<TransportContainer, ConsumerMessage.CommittableOffset>create()
                .map(tc->{
                    log.info("Processing TC: {}" ,tc.getTcId());
                    return tc;
                })
                .asFlow()
                .via(syncFlow)
                .<TransportContainer,ConsumerMessage.CommittableOffset,ConsumerMessage.CommittableOffset>asFlowWithContext(Pair::create,Pair::second)
                .map(Pair::first);
    }
}
