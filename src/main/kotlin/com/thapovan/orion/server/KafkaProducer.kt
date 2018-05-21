package com.thapovan.orion.server

import com.thapovan.orion.proto.Span
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.BytesSerializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.logging.log4j.LogManager
import java.lang.Exception
import java.util.*


object KafkaProducer {

    private val producerProperties: Properties = Properties()
    private val producer: KafkaProducer<String,ByteArray>
    private val LOG = LogManager.getLogger(this.javaClass)

    init {
        producerProperties.load(ClassLoader.getSystemResourceAsStream("kafka_producer.properties"))
        producerProperties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName
        producerProperties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = ByteArraySerializer::class.qualifiedName
        producer = KafkaProducer(producerProperties)
    }

    fun pushSpanEvent(span: Span) {
        val eventID = when (span.eventCase) {
            null -> return@pushSpanEvent
            Span.EventCase.START_EVENT -> span.startEvent.eventId
            Span.EventCase.END_EVENT -> span.endEvent.eventId
            Span.EventCase.LOG_EVENT -> span.logEvent.eventId
            Span.EventCase.EVENT_NOT_SET -> return@pushSpanEvent
        }
        val key = "${span.traceContext.traceId}_${span.spanId}_${eventID}"
        val value:ByteArray = span.toByteArray()
        val producerRecord = ProducerRecord("incoming-request",key,value)
        producer.send(producerRecord, { recordMetaData: RecordMetadata, exception: Exception? ->
            if (exception != null) {
                LOG.error("Error when pushing record to kafka broken: ${exception.message}",exception)
            } else {
                LOG.info("Published record. offset: ${recordMetaData.offset()}")
            }
        })
    }

    fun flush() = producer.flush()
}