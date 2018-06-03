package com.thapovan.kafka;

import com.google.gson.Gson;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Properties;

public class KafkaESSummaryConsumer implements Runnable
{
    private static final Logger LOG = LoggerFactory.getLogger(KafkaESSummaryConsumer.class);

    private static final Gson gson = new Gson();

    private static final String TOPIC_NAME = "trace-summary-json";

    private static final String ES_HOST = "localhost";

    private static final int ES_PORT = 9300;

    private KafkaConsumer<String, String> consumer = null;

    private TransportClient client = null;


    public KafkaESSummaryConsumer()
    {
        Properties props = new Properties();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:29092");

        props.put(ConsumerConfig.GROUP_ID_CONFIG, "summaryConsumerGroup");

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        consumer = new KafkaConsumer(props);

        //es search

        try
        {
            client = new PreBuiltTransportClient(Settings.EMPTY)
                    .addTransportAddress(new TransportAddress(InetAddress.getByName(ES_HOST), ES_PORT));
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();
        }

    }

    @Override
    public void run()
    {
        try
        {
            consumer.subscribe(Arrays.asList(TOPIC_NAME));

            while (true)
            {
                ConsumerRecords<String, String> records = consumer.poll(100);

                BulkRequestBuilder bulkRequest = client.prepareBulk();

                for (ConsumerRecord<String, String> record : records)
                {
                    bulkRequest.add(client.prepareIndex("summaries", "_json", record.key())
                            .setSource(record.value())).get();
                }

                consumer.commitAsync();

                BulkResponse bulkResponse = bulkRequest.get();

                ESUtil.logResponse(bulkResponse);

                bulkRequest = null;
            }
        }
        catch (WakeupException e)
        {
            LOG.error("Kafka exception occurred", e);
        }
        finally
        {
            consumer.commitSync();
            consumer.close();
        }
    }
}
