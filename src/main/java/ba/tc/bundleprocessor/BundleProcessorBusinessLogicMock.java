package ba.tc.bundleprocessor;

import akka.Done;
import akka.NotUsed;
import akka.kafka.ConsumerMessage;
import akka.stream.javadsl.FlowWithContext;
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;

public class BundleProcessorBusinessLogicMock implements BundleProcessorBusinessLogic{
    @Override
    public FlowWithContext<Bundle, ConsumerMessage.CommittableOffset, Done, ConsumerMessage.CommittableOffset, NotUsed> getFlow() {
        return FlowWithContext.<Bundle, ConsumerMessage.CommittableOffset>create()
                              .map(bundle->{
                                  System.out.println(format(bundle));
                                  return Done.getInstance();
                              });
    }
    private String format(Bundle bundle) {
        return String.format("Processing bundle: [bundleId:%s; tcId:%s: pdfs:%s]",bundle.getBundleId(),bundle.getTcId(),bundle.getPdfs().size());
    }
}
