package ba.tc.bundleprocessor;

import akka.Done;
import akka.NotUsed;
import akka.kafka.ConsumerMessage;
import akka.stream.javadsl.FlowWithContext;
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;

import java.util.List;

public interface BundleProcessorBusinessLogic {
    public FlowWithContext<Bundle,  ConsumerMessage.CommittableOffset, Done,  ConsumerMessage.CommittableOffset, NotUsed> getFlow();
}
