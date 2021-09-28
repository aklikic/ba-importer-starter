package ba.tc.tcprocessor.sync;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.*;
import akka.japi.Pair;
import akka.kafka.ConsumerMessage;
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;
import lombok.Value;

import java.util.List;

public class ActorBlockingFacilitator {

    public interface Command{}
    @Value
    public static class Request implements Command{
        Pair<TransportContainer, ConsumerMessage.CommittableOffset> tcWithContext;
        ActorRef<Response> replyTo;
    }
    @Value
    public static class Response implements Command{
        Pair<List<Bundle>,ConsumerMessage.CommittableOffset> bundles;
    }
    private static Behavior<Command> createWorker(Integer workTimeoutInMillis) {
        return Behaviors.setup(ctx-> worker(ctx,workTimeoutInMillis));
    }
    private static Behavior<Command> worker(final ActorContext<Command> context,Integer workTimeoutInMillis) {
        context.getLog().info("Starting worker {}!",context.getSelf().path().name());
        return Behaviors.receive(Command.class)
                .onMessage(Request.class, command -> onRequest(context, command,workTimeoutInMillis))
                .build();
    }

    private static Behavior<Command> onRequest(ActorContext<Command> context,Request command,Integer workTimeoutInMillis) {
        List<Bundle> bundles = BlockingMockLogic.getBundlesFromTcSync(command.getTcWithContext().first(),workTimeoutInMillis);
        command.getReplyTo().tell(new Response(Pair.create(bundles,command.getTcWithContext().second())));
        return Behaviors.same();
    }

    public static ActorRef<Command> create(ActorContext context, final int parallelism,final int workTimeoutInMillis){
        context.getLog().info("Starting router: {}",context.getSelf().path());
        PoolRouter<Command> pool =
                Routers.pool(
                        parallelism,
                        // make sure the workers are restarted if they fail
                        Behaviors.supervise(createWorker(workTimeoutInMillis)).onFailure(SupervisorStrategy.restart()))
                        .withRoundRobinRouting()
                        .withRouteeProps(DispatcherSelector.blocking());
        //or you can configure custom dispatcher also: https://doc.akka.io/docs/akka/current/typed/dispatchers.html

        return context.spawn(pool, "worker-pool",DispatcherSelector.sameAsParent());

        /*return
        Behaviors.setup(
                context -> {
                    context.getLog().info("Starting router: {}",context.getSelf().path());
                    PoolRouter<Command> pool =
                            Routers.pool(
                                    parallelism,
                                    // make sure the workers are restarted if they fail
                                    Behaviors.supervise(createWorker(workTimeoutInMillis)).onFailure(SupervisorStrategy.restart()))
                            .withRoundRobinRouting()
                            .withRouteeProps(DispatcherSelector.blocking());
                    //or you can configure custom dispatcher also: https://doc.akka.io/docs/akka/current/typed/dispatchers.html

                    //ActorRef<Command> router = context.spawn(pool, "worker-pool",DispatcherSelector.sameAsParent());
                    return Behaviors.empty();
                });*/
    }
}
