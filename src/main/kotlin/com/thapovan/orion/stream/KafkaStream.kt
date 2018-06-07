package com.thapovan.orion.stream

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.thapovan.orion.data.LogObject
import com.thapovan.orion.data.MetaDataObject
import com.thapovan.orion.data.SpanNode
import com.thapovan.orion.proto.Span
import com.thapovan.orion.util.TraceIdPartitioner
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.logging.log4j.LogManager
import java.util.*

class KafkaStream {

    private var kafkaStream: KafkaStreams? = null
    private var LOG = LogManager.getLogger(KafkaStream::class.java)

    fun start(streamConfig: Properties) {
        if (kafkaStream != null) {
            if (kafkaStream?.state()!!.isRunning) {
                return
            }
        }
        streamConfig[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String()::class.java.name
        streamConfig[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.ByteArray()::class.java.name
        streamConfig[ProducerConfig.PARTITIONER_CLASS_CONFIG] = TraceIdPartitioner::class.java.name

        val streamBuilder = StreamsBuilder()

        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
        val spanNodeType = object : TypeToken<SpanNode>() {}.type
        val metadataObjectType = object : TypeToken<MetaDataObject>() {}.type
        val logObjectArrayType = object : TypeToken<MutableList<LogObject>>() {}.type

        val incomingRequestStream = streamBuilder.stream<String, ByteArray>("incoming-request")

        val incomingRequestSpanStream = incomingRequestStream
            .mapValues {
                Span.parseFrom(it)
            }

        JsonDebugStream.buildGraph(streamBuilder, incomingRequestStream)
        SpanEventSegregator.buildGraph(streamBuilder, incomingRequestStream)

        val protoSpanStartStopEventStream = streamBuilder.stream<String, ByteArray>("proto-span-start-stop")
            .mapValues {
                Span.parseFrom(it)
            }

        SpanLifecycleProcessor.buildGraph(streamBuilder, incomingRequestSpanStream)

        val spanStartStopRaw = streamBuilder.stream<String, ByteArray>("span-start-stop")
        val spanStartStop = spanStartStopRaw
            .mapValues {
                gson.fromJson<SpanNode>(String(it), spanNodeType)
            }

        val protoLogEventStream = streamBuilder.stream<String, ByteArray>("proto-span-log")
            .mapValues {
                Span.parseFrom(it)
            }

        val metaDataObject = streamBuilder.stream<String, ByteArray>("span-metadata")
            .mapValues {
                gson.fromJson<MetaDataObject>(String(it), metadataObjectType)
            }

        val spanLogAggregateStream = streamBuilder.stream<String, ByteArray>("span-log-aggregated")
//            .mapValues {
//                gson.fromJson<MutableList<LogObject>>(String(it),logObjectArrayType)
//            }

        SpanLogAggregator.buildGraph(streamBuilder, protoSpanStartStopEventStream, protoLogEventStream)
//        FootprintBuilder.buildGraph(streamBuilder,incomingRequestStream,spanStartStop)

        FatTraceObject.buildGraph(streamBuilder, spanLogAggregateStream, spanStartStopRaw)

        val fatTraceObjectStream = streamBuilder.stream<String, ByteArray>("fat-trace-object")

        TraceSummaryBuilder.buildGraph(streamBuilder, fatTraceObjectStream, metaDataObject)

        kafkaStream = KafkaStreams(streamBuilder.build(), streamConfig)
        kafkaStream?.cleanUp()
        kafkaStream?.start()
        LOG.info("Kafka stream processor started")
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                val LOG = LogManager.getLogger(this@KafkaStream.javaClass)
                LOG.info("Kafka stream shutting down since JVM is shutting down")
                this@KafkaStream.stop()
                LOG.info("Kafka stream has been stopped")
            }
        })
    }

    fun stop() {
        kafkaStream?.close()
    }

    companion object {
        //TODO: Must be configurable
        const val WINDOW_DURATION_MS = 5L * 60L * 1000L // 5 mins
        const val WINDOW_SLIDE_DURATION_MS = 1000L // 1 min
    }
}