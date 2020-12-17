package ba.tc.tcprocessor;

import akka.NotUsed;
import akka.kafka.ConsumerMessage;
import akka.stream.javadsl.FlowWithContext;
import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;

import java.util.List;

public interface TcProcessorBusinessLogic {
    public FlowWithContext<TransportContainer,  ConsumerMessage.CommittableOffset, List<Bundle>,  ConsumerMessage.CommittableOffset, NotUsed> getFlow();
}
