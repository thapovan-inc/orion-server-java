package com.thapovan.orion;

import com.thapovan.orion.server.TracerGrpcServer;
import com.thapovan.orion.server.TracerHttpServer;
import com.thapovan.orion.stream.KafkaStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Spark;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class Main {
    private static Logger LOG = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        boolean startTracerGrpc = false;
        boolean startTracerREST = false;
        boolean startKafkaStreams = false;

        List<String> argsList = new ArrayList();
        Collections.addAll(argsList,args);
        if (argsList.size() > 0) {
            startTracerGrpc = argsList.contains("TracerGrpc");
            startTracerREST = argsList.contains("TracerREST");
            startKafkaStreams = argsList.contains("KafkaStreams");
        } else {
            startTracerGrpc = true;
            startKafkaStreams = true;
            startTracerREST = true;
        }
        try {
            TracerGrpcServer server = null;
            if(startTracerGrpc) {
               server = new TracerGrpcServer();
               server.start(20691);
            }

            TracerHttpServer httpServer = null;
            if(startTracerREST) {
                httpServer = new TracerHttpServer();
                httpServer.start(9017);
            }

            if (startKafkaStreams) {
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
            }

            if (startTracerGrpc) {
                server.blockUntilShutdown();
            }
        } catch (IOException e) {
            LOG.error("IOException occured", e);
        } catch (InterruptedException e) {
            LOG.error("InterruptedException occured", e);
        } catch (Throwable e) {
            LOG.error("Uncaught error has emerge", e);
        }
    }
}
