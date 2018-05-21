import com.thapovan.orion.Main;
import com.thapovan.orion.proto.*;
import com.thapovan.orion.server.KafkaProducer;
import com.thapovan.orion.server.TracerServer;
import com.thapovan.orion.stream.KafkaStream;
import io.grpc.netty.NettyChannelBuilder;
import org.junit.BeforeClass;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SimpleSpanEventGenerator {

    static TracerServer server;
    static KafkaStream stream;

    @BeforeAll
    public static void startServer() throws IOException {
        server = new TracerServer();
        server.start(20691);
        Properties kafkaStreamProperties = new Properties();
        kafkaStreamProperties.load(ClassLoader.getSystemResourceAsStream("kafka_stream.properties"));

        stream = new KafkaStream();
        stream.start(kafkaStreamProperties);
    }

    @Test
    public void spanEventGenerator() {
        try {
            TracerGrpc.TracerBlockingStub client = TracerGrpc.newBlockingStub(NettyChannelBuilder
                    .forAddress("localhost", 20691)
                    .usePlaintext()
                    .build());
            StartEvent spanStartEvent = StartEvent.newBuilder().setEventId(System.nanoTime()).build();
            Trace traceContext = Trace.newBuilder().setTraceId(UUID.randomUUID().toString()).build();
            Span simpleSpan = Span.newBuilder().setStartEvent(spanStartEvent)
                    .setSpanId(UUID.randomUUID().toString())
                    .setTraceContext(traceContext)
                    .build();
            UnaryRequest request = UnaryRequest.newBuilder().setSpanData(simpleSpan).build();
            ServerResponse response = client.uploadSpan(request);
            assertEquals(true, response.getSuccess(), "Expected success field in the response to be true");
            KafkaProducer.INSTANCE.flush();
            Thread.sleep(10000);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

    }

    @AfterAll
    public static void stopServer() {
        server.stop();
        stream.stop();
    }
}
