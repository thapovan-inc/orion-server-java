package com.thapovan.orion.stream

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.thapovan.orion.data.MetaDataObject
import com.thapovan.orion.data.SpanNode
import com.thapovan.orion.proto.Span
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

        val streamBuilder = StreamsBuilder()

        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
        val spanNodeType = object : TypeToken<SpanNode>() {}.type
        val metadataObjectType = object : TypeToken<MetaDataObject>() {}.type

        val incomingRequestStream = streamBuilder.stream<String,ByteArray>("incoming-request")

        JsonDebugStream.buildGraph(streamBuilder,incomingRequestStream)
        SpanEventSegregator.buildGraph(streamBuilder,incomingRequestStream)

        val protoSpanStartStopEventStream
                = streamBuilder.stream<String,ByteArray>("proto-span-start-stop")
            .mapValues {
                Span.parseFrom(it)
            }

        SpanLifecycleProcessor.buildGraph(streamBuilder,protoSpanStartStopEventStream)
        FootprintBuilder.buildGraph(streamBuilder,incomingRequestStream)

        val spanStartStop = streamBuilder.stream<String,ByteArray>("span-start-stop")
            .mapValues {
                gson.fromJson<SpanNode>(String(it),spanNodeType)
            }

        val protoLogEventStream = streamBuilder.stream<String,ByteArray>("proto-span-log")
            .mapValues {
                Span.parseFrom(it)
            }

        val metaDataObject = streamBuilder.stream<String,ByteArray>("span-metadata")
            .mapValues {
                gson.fromJson<MetaDataObject>(String(it),metadataObjectType)
            }

        TraceSummaryBuilder.buildGraph(streamBuilder,spanStartStop,metaDataObject)

        kafkaStream = KafkaStreams(streamBuilder.build(),streamConfig)
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
    }
}