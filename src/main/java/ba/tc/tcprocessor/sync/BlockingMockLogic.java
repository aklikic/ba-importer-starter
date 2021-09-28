package ba.tc.tcprocessor.sync;

import ba.tc.datamodel.Bundle;
import ba.tc.datamodel.TransportContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BlockingMockLogic {
    private static Logger log = LoggerFactory.getLogger(BlockingMockLogic.class);

    public static List<Bundle> getBundlesFromTcSync(TransportContainer tc, int workTimeoutInMillis){
        log.info("[{}] START blocking {}",tc.getTcId(),Thread.currentThread().getName());
        try {
            //sleep 10 millis less then timeout
            Thread.sleep(workTimeoutInMillis-10);
        } catch (InterruptedException e) {
        }
        log.info("[{}] END blocking",tc.getTcId());
        return Arrays.asList(createBundle(tc.getTcId().toString()), createBundle(tc.getTcId().toString()));
    }
    private static Bundle createBundle(String tcId) {
        return Bundle.newBuilder().setTcId(tcId)
                .setBundleId(UUID.randomUUID().toString())
                .setMetadata("some metadata")
                .setPdfs(Arrays.asList("pdf1Uri", "pdf2Uri"))
                .build();
    }
}
