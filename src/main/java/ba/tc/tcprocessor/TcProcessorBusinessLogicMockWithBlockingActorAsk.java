package ba.tc.tcprocessor;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.japi.Pair;
import akka.kafka.ConsumerMessage;
import akka.stream.javadsl.FlowWithContext;
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;
import ba.tc.tcprocessor.sync.ActorBlockingFacilitator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class TcProcessorBusinessLogicMockWithBlockingActorAsk implements  TcProcessorBusinessLogic{
    private static Logger log = LoggerFactory.getLogger(TcProcessorBusinessLogicMockWithBlockingActorAsk.class);
    private final ActorSystem system;
    private final int parallelism;
    private final Duration timeout;
    private final ActorRef<ActorBlockingFacilitator.Command> workerRouter;

    public TcProcessorBusinessLogicMockWithBlockingActorAsk(ActorSystem system,ActorRef<ActorBlockingFacilitator.Command> workerRouter, int parallelism, int workTimeoutSec){
        this.system = system;
        this.workerRouter = workerRouter;
        this.parallelism = parallelism;
        this.timeout = Duration.ofSeconds(workTimeoutSec);
    }

    public FlowWithContext<TransportContainer,  ConsumerMessage.CommittableOffset, List<Bundle>,  ConsumerMessage.CommittableOffset, NotUsed> getFlow() {

        return FlowWithContext.<TransportContainer, ConsumerMessage.CommittableOffset>create()
                .map(tc->{
                    log.info("Processing TC: {}" ,tc.getTcId());
                    return tc;
                })

                .asFlow()
                .mapAsync(parallelism,this::ask)
                .<TransportContainer,ConsumerMessage.CommittableOffset,ConsumerMessage.CommittableOffset>asFlowWithContext(Pair::create,Pair::second)
                .map(Pair::first);
    }

    private CompletionStage<Pair<List<Bundle>,ConsumerMessage.CommittableOffset>> ask(Pair<TransportContainer,ConsumerMessage.CommittableOffset> tcWithContext){
        return AskPattern.<ActorBlockingFacilitator.Command,ActorBlockingFacilitator.Response>ask(
                workerRouter,
                replyTo->new ActorBlockingFacilitator.Request(tcWithContext,replyTo),
                timeout,
                system.scheduler())
                .thenApply(ActorBlockingFacilitator.Response::getBundles);
    }
}
