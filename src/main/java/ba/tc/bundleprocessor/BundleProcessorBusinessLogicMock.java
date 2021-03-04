package ba.tc.bundleprocessor;

import akka.Done;
import akka.NotUsed;
import akka.japi.pf.PFBuilder;
import akka.kafka.ConsumerMessage;
import akka.stream.Materializer;
import akka.stream.RestartSettings;
import akka.stream.javadsl.*;
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.PartialFunction;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class BundleProcessorBusinessLogicMock implements BundleProcessorBusinessLogic{
    private static Logger log = LoggerFactory.getLogger(BundleProcessorBusinessLogicMock.class);
    private RestartSettings restartSettings = RestartSettings.create(Duration.ofSeconds(1),Duration.ofSeconds(2),0.2)
                                                             .withMaxRestarts(3,Duration.ofSeconds(10));

    private Materializer materializer;

    public BundleProcessorBusinessLogicMock(Materializer materializer){
        this.materializer = materializer;
    }

    @Override
    public FlowWithContext<Bundle, ConsumerMessage.CommittableOffset, Done, ConsumerMessage.CommittableOffset, NotUsed> getFlow() {
        return FlowWithContext.<Bundle, ConsumerMessage.CommittableOffset>create()
                              .mapAsync(1,this::processBundleWithRetries);
    }

    private CompletionStage<Done> processBundleWithRetries(Bundle bundle){
        Source<Done,NotUsed> responseSource = Source.completionStage(processBundle(bundle));


        return
        RestartSource.withBackoff(restartSettings,()->responseSource)
                     .recover(onFailedRetries(bundle))
                     .runWith(Sink.head(),materializer);
    }
    private CompletionStage<Done> processBundle(Bundle bundle){
        log.info("Processing bundle: [bundleId:{}; tcId:{}: pdfs:{}]...",bundle.getBundleId(),bundle.getTcId(),bundle.getPdfs().size());
        if(bundle.getBundleId().toString().hashCode() % 5 == 0 ){
            CompletableFuture cf = new CompletableFuture();
            cf.completeExceptionally(new RuntimeException("BundleId "+bundle.getBundleId()+" hashCode is mode 5"));
            return cf;
        }
        log.info("SUCCESS processing bundle: [bundleId:{}; tcId:{}: pdfs:{}]",bundle.getBundleId(),bundle.getTcId(),bundle.getPdfs().size());
        //simulating asynchronous processing
        return CompletableFuture.completedFuture(Done.getInstance());
    }

    private PartialFunction<Throwable, Done> onFailedRetries(Bundle bundle){
        return new PFBuilder<Throwable, Done>()
                .match(RuntimeException.class, ex -> {
                    log.info("ERROR processing bundle: [bundleId:{}; tcId:{}: pdfs:{}]",bundle.getBundleId(),bundle.getTcId(),bundle.getPdfs().size());
                    return Done.getInstance();
                })
                .match(Throwable.class, ex -> {
                    log.error("ERROR 2 processing bundle: [bundleId:{}; tcId:{}: pdfs:{}]",bundle.getBundleId(),bundle.getTcId(),bundle.getPdfs().size());
                    return Done.getInstance();
                })
                .matchAny(ex->{
                    log.error("ERROR 3 processing bundle: [bundleId:{}; tcId:{}: pdfs:{}]",bundle.getBundleId(),bundle.getTcId(),bundle.getPdfs().size());
                    return Done.getInstance();
                })
                .build();
    }



}
