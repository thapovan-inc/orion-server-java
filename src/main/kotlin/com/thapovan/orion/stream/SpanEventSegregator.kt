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

package com.thapovan.orion.stream

import com.google.gson.GsonBuilder
import com.thapovan.orion.data.MetaDataObject
import com.thapovan.orion.proto.Span
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Predicate

object SpanEventSegregator {
    fun buildGraph(streamsBuilder: StreamsBuilder, incomingRequest: KStream<String, ByteArray>) {

        val gson = GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .serializeNulls()
            .create()

        val startStopPredicate: Predicate<String, Span> =
            Predicate { key: String, value: Span -> value.hasStartEvent() || value.hasEndEvent() }
        val logEventPredicate: Predicate<String, Span> = Predicate { key, value -> value.hasLogEvent() }

        val requestStream = incomingRequest
            .mapValues {
                return@mapValues Span.parseFrom(it)
            }
        val branches = requestStream.branch(
            startStopPredicate,
            logEventPredicate
        )
        val startStopStream = branches[0]
        val logStream = branches[1]
        val metadataStream = requestStream
            .mapValues {
                var metadataString: String
                var eventID: Long
                var logLevel: String
                when {
                    it.hasStartEvent() -> {
                        metadataString = it.startEvent.jsonString
                        eventID = it.startEvent.eventId
                        logLevel = "START"
                    }
                    it.hasEndEvent() -> {
                        metadataString = it.endEvent.jsonString
                        eventID = it.endEvent.eventId
                        logLevel = "END"
                    }
                    it.hasLogEvent() -> {
                        metadataString = it.logEvent.jsonString
                        eventID = it.logEvent.eventId
                        logLevel = it.logEvent.level.name
                    }
                    else -> {
                        return@mapValues null
                    }
                }
                if (metadataString.isNullOrBlank()) {
                    return@mapValues null
                } else {
                    MetaDataObject(it.spanId, eventID, it.timestamp, logLevel, metadataString)
                }
            }
            .filter { _, value -> value != null }
            .mapValues {
                gson.toJson(it).toByteArray()
            }
        metadataStream.to("span-metadata")
        startStopStream
            .mapValues {
                it.toBuilder().setInternalSpanRefNumber(System.nanoTime()).build()
            }
            .mapValues { it.toByteArray() }
            .to("proto-span-start-stop")
        logStream
            .mapValues {
                it.toByteArray()
            }
            .to("proto-span-log")
    }
}