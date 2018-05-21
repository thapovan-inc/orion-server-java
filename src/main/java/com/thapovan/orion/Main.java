package com.thapovan.orion;

import com.thapovan.orion.server.TracerServer;
import com.thapovan.orion.stream.KafkaStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Properties;

public class Main {
    private static Logger LOG = LogManager.getLogger(Main.class);

    public static void main(String[] args){
        try {
            TracerServer server = new TracerServer();
            server.start(20691);

            Properties kafkaStreamProperties = new Properties();
            kafkaStreamProperties.load(ClassLoader.getSystemResourceAsStream("kafka_stream.properties"));

            KafkaStream stream = new KafkaStream();
            stream.start(kafkaStreamProperties);

            server.blockUntilShutdown();
        } catch (IOException e) {
            LOG.error("IOException occured",e);
        } catch (InterruptedException e) {
            LOG.error("InterruptedException occured",e);
        } catch (Throwable e) {
            LOG.error("Uncaught error has emerge",e);
        }
    }
}
