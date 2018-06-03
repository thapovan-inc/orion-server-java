package com.thapovan.orion;

import com.thapovan.kafka.KafkaESFootPrintConsumer;
import com.thapovan.kafka.KafkaESSummaryConsumer;
import com.thapovan.orion.server.TracerGrpcServer;
import com.thapovan.orion.server.TracerHttpServer;
import com.thapovan.orion.stream.KafkaStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static Logger LOG = LogManager.getLogger(Main.class);

    public static void main(String[] args){
        try {
            TracerGrpcServer server = new TracerGrpcServer();
            server.start(20691);

            TracerHttpServer httpServer = new TracerHttpServer();
            httpServer.start(9017);

            Properties kafkaStreamProperties = new Properties();
            InputStream kafkaStreamPropertiesStream;
            String kafkaStreamPropertiesFile = System.getenv("KAFKA_STREAM_PROPERTIES");
            if (kafkaStreamPropertiesFile != null && kafkaStreamPropertiesFile.length() > 0) {
                kafkaStreamPropertiesStream = new FileInputStream(kafkaStreamPropertiesFile);
            } else {
                kafkaStreamPropertiesStream = ClassLoader.getSystemResourceAsStream("kafka_stream.properties");
            }
            kafkaStreamProperties.load(kafkaStreamPropertiesStream);

            KafkaStream stream = new KafkaStream();
            stream.start(kafkaStreamProperties);

            server.blockUntilShutdown();

            ExecutorService executorService = Executors.newFixedThreadPool(2);

            executorService.execute(new KafkaESSummaryConsumer());

            executorService.execute(new KafkaESFootPrintConsumer());

            executorService.shutdown();
        } catch (IOException e) {
            LOG.error("IOException occured",e);
        } catch (InterruptedException e) {
            LOG.error("InterruptedException occured",e);
        } catch (Throwable e) {
            LOG.error("Uncaught error has emerge",e);
        }
    }
}
