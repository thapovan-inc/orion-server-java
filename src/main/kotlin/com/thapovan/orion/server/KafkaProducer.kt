/*
 * Copyright (c) 2018 Thapovan info Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thapovan.orion.server

import com.thapovan.orion.proto.Span
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.logging.log4j.LogManager
import java.io.FileInputStream
import java.io.InputStream
import java.lang.Exception
import java.util.*


object KafkaProducer {

    private val producerProperties: Properties = Properties()
    private val producer: KafkaProducer<String, ByteArray>
    private val LOG = LogManager.getLogger(this.javaClass)

    const val REQUEST_TOPIC = "incoming-request"

    init {
        val producerPropertiesFile = System.getenv("KAFKA_PRODUCER_PROPERTIES")
        var producerPropertiesStream: InputStream
        producerPropertiesStream = if (producerPropertiesFile != null) {
            FileInputStream(producerPropertiesFile)
        } else {
            ClassLoader.getSystemResourceAsStream("kafka_producer.properties")
        }
        producerProperties.load(producerPropertiesStream)
        producerProperties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName
        producerProperties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = ByteArraySerializer::class.qualifiedName
        producer = KafkaProducer(producerProperties)
    }

    fun pushSpanEvent(span: Span) {
        val eventID: Long = when (span.eventCase) {
            null -> {
                LOG.debug("Event is null")
                return@pushSpanEvent
            }
            Span.EventCase.START_EVENT -> span.startEvent.eventId
            Span.EventCase.END_EVENT -> span.endEvent.eventId
            Span.EventCase.LOG_EVENT -> span.logEvent.eventId
            Span.EventCase.EVENT_NOT_SET -> {
                LOG.debug("Event not set")
                return@pushSpanEvent
            }
        }
        val normSpandId = span.spanId.toLowerCase()
        val normTraceId = span.traceContext.traceId.toLowerCase()
        val normParentSpanId = span.parentSpanId?.toLowerCase()

        //new objects
        val newSpanBuilder = span.toBuilder()
        val newTraceContextBuilder = span.traceContext.toBuilder()
        newTraceContextBuilder.setTraceId(normTraceId)
        val newTrace = newTraceContextBuilder.build()


        newSpanBuilder.setSpanId(normSpandId).setParentSpanId(normParentSpanId).setTraceContext(newTrace)
        if (newSpanBuilder.hasStartEvent()) {
            newSpanBuilder.internalSpanRefNumber = System.nanoTime()
        }
        val newSpan = newSpanBuilder.build()
        val key = "${normTraceId}_${normSpandId}_$eventID"
        val value: ByteArray = newSpan.toByteArray()
        val partition = key[0].toInt().rem(4)
        val producerRecord = ProducerRecord(REQUEST_TOPIC, partition, key, value)
        producer.send(producerRecord, { recordMetaData: RecordMetadata, exception: Exception? ->
            if (exception != null) {
                LOG.error("Error when pushing record to kafka broken: ${exception.message}", exception)
            } else {
                LOG.info("Published record. offset: ${recordMetaData.offset()}")
            }
        })
    }

    fun flush() = producer.flush()
}