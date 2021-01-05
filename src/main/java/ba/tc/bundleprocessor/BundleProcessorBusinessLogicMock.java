package ba.tc.bundleprocessor;

import akka.Done;
import akka.NotUsed;
import akka.kafka.ConsumerMessage;
import akka.stream.javadsl.FlowWithContext;
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BundleProcessorBusinessLogicMock implements BundleProcessorBusinessLogic{
    private static Logger log = LoggerFactory.getLogger(BundleProcessorBusinessLogicMock.class);
    @Override
    public FlowWithContext<Bundle, ConsumerMessage.CommittableOffset, Done, ConsumerMessage.CommittableOffset, NotUsed> getFlow() {
        return FlowWithContext.<Bundle, ConsumerMessage.CommittableOffset>create()
                              .map(bundle->{
                                  log.info("Processing bundle: [bundleId:{}; tcId:{}: pdfs:{}]",bundle.getBundleId(),bundle.getTcId(),bundle.getPdfs().size());
                                  return Done.getInstance();
                              });
    }
}
