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
import com.google.gson.reflect.TypeToken
import com.thapovan.orion.data.SpanNode
import com.thapovan.orion.proto.Span
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.TimeWindows

object SpanLifecycleProcessor {
    fun buildGraph(streamsBuilder: StreamsBuilder, protoSpanStartStopEventStream: KStream<String, Span>) {
        val aggTypeToken = object : TypeToken<SpanNode>() {}.type
        val gson = GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .serializeNulls()
            .create()

        val startStopSpan = protoSpanStartStopEventStream
            .mapValues {
                it.toByteArray()
            }
            .groupBy { key, value ->
                val parts = key.split("_")
                return@groupBy "${parts[0]}_${parts[1]}"
            }
            .windowedBy(TimeWindows.of(KafkaStream.WINDOW_DURATION_MS))
            .aggregate(
                {
                    gson.toJson(SpanNode(""),aggTypeToken).toByteArray()
                },
                {
                        key, spanArr, bValueAggregate ->
                    val spanNode =
                        gson.fromJson<SpanNode>(String(bValueAggregate), aggTypeToken)
                    val span = Span.parseFrom(spanArr)
                    val newSpan = if (span.hasStartEvent()) {
                        SpanNode(
                            span.spanId,
                            if (span.serviceName.isNullOrEmpty()) spanNode.serviceName else span.serviceName,
                            if (span.parentSpanId.isNullOrEmpty()) spanNode.parentId else span.parentSpanId,
                            span.timestamp,
                            spanNode.endTime
                        )
                    } else if (span.hasEndEvent()) {
                        SpanNode(
                            span.spanId,
                            if (span.serviceName.isNullOrEmpty()) spanNode.serviceName else span.serviceName,
                            if (span.parentSpanId.isNullOrEmpty()) spanNode.parentId else span.parentSpanId,
                            spanNode.startTime,
                            span.timestamp
                        )
                    } else {
                        spanNode
                    }
                    gson.toJson(newSpan,aggTypeToken).toByteArray()
                },
                Materialized.with(Serdes.String(), Serdes.ByteArray())
            )
            .toStream()
            .selectKey { key, _ ->  key.key() }

        startStopSpan.to("span-start-stop")
    }
}