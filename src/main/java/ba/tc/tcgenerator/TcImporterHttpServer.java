package ba.tc.tcgenerator;

import akka.Done;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.japi.function.Function;
import akka.kafka.javadsl.Producer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import ba.tc.Serializers;
import ba.tc.TopicProducer;
import ba.tc.datamodel.TransportContainer;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

public class TcImporterHttpServer {

    private static Logger log = LoggerFactory.getLogger(TcImporterHttpServer.class);

    private final ActorSystem system;
    private final Materializer materializer;
    private final String host;
    private final int port;
    private final String tcTopic;

    public TcImporterHttpServer(ActorSystem system,Materializer materializer){
        this.system = system;
        this.materializer = materializer;
        this.host = system.settings().config().getString("tc-importer.host");
        this.port = system.settings().config().getInt("tc-importer.port");
        this.tcTopic = system.settings().config().getString("topic.tc");

    }

    public CompletionStage<ServerBinding> start(Flow<TransportContainer, Done,?> processFlow){
        final Http http = Http.get(system);
        Flow<HttpRequest, HttpResponse,?> flow =
                Flow.<HttpRequest>create()
                        .mapAsync(1,req->req.entity().toStrict(req.entity().getContentLengthOption().getAsLong(),materializer))
                        .map(tcDeSerializer)
                        .via(processFlow)
                        .map(d->HttpResponse.create().withStatus(StatusCodes.OK));
        return http.newServerAt(host, port).bindFlow(flow)
                .thenApply(b->{
                    log.info("Bounded!");
                    return b;
                });
    }

    public static Flow<TransportContainer, Done,?> processFlow(ActorSystem system,TopicProducer topicProducer){
        String tcTopic = system.settings().config().getString("topic.tc");
        return
        Flow.<TransportContainer>create()
                .map(tc->{
                    log.info("HTTP TC: {}",tc.getTcId());
                    return tc;
                })
                .map(tc-> Serializers.tcProducerEnvelopeSerializer.apply(tc,tcTopic))
                .via(Producer.flexiFlow(topicProducer.producerSettings()))
                .map(c->Done.getInstance());
    }

    private static Function<HttpEntity.Strict, TransportContainer> tcDeSerializer = (data)-> {
        DatumReader<TransportContainer> reader
                = new SpecificDatumReader<>(TransportContainer.class);
        Decoder decoder = null;
        try {
            decoder = DecoderFactory.get().jsonDecoder(
                    TransportContainer.getClassSchema(), data.getData().decodeString("utf8"));
            return reader.read(null, decoder);
        } catch (IOException e) {
            //throw new RuntimeException(e);
            return null;
        }

    };

    public static void main(String[] args) throws Exception{
        ActorSystem system = ActorSystem.create("http-test");
        Materializer materializer = Materializer.createMaterializer(system);
        Flow<TransportContainer, Done,?> processFlow =
                Flow.<TransportContainer>create()
                        .map(tc->{
                            System.out.println(tc.toString());
                            return tc;
                        })
                .map(tc->Done.getInstance());
        TcImporterHttpServer s = new TcImporterHttpServer(system,materializer);
        s.start(processFlow);
        Thread.sleep(10000);
    }
}
