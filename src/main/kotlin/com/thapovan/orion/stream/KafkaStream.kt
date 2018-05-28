package com.thapovan.orion.stream

import com.google.protobuf.util.JsonFormat
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

        FootprintBuilder.buildGraph(streamBuilder)
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
}