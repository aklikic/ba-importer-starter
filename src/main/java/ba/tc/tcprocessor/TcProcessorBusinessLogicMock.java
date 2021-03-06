package ba.tc.tcprocessor;

import akka.NotUsed;
import akka.kafka.ConsumerMessage;
import akka.stream.javadsl.FlowWithContext;
import ba.tc.bundleprocessor.BundleProcessorBusinessLogicMock;
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TcProcessorBusinessLogicMock implements  TcProcessorBusinessLogic{
    private static Logger log = LoggerFactory.getLogger(TcProcessorBusinessLogicMock.class);
    private final Integer recordsPerSecond = 1;

    public FlowWithContext<TransportContainer,  ConsumerMessage.CommittableOffset, List<Bundle>,  ConsumerMessage.CommittableOffset, NotUsed> getFlow() {
        return FlowWithContext.<TransportContainer, ConsumerMessage.CommittableOffset>create()
                             .throttle(recordsPerSecond, Duration.ofSeconds(1))
                             .map(tc -> {
                                 //log.info("Processing TC: {}" ,tc.getTcId());
                                return getBundlesFromTc(tc);
                             });
    }

    private List<Bundle> getBundlesFromTc(TransportContainer tc){
        return Arrays.asList(createBundle(tc.getTcId().toString()), createBundle(tc.getTcId().toString()));
    }
    private Bundle createBundle(String tcId) {
        return Bundle.newBuilder().setTcId(tcId)
                .setBundleId(UUID.randomUUID().toString())
                .setMetadata("some metadata")
                .setPdfs(Arrays.asList("pdf1Uri", "pdf2Uri"))
                .build();
    }
}
