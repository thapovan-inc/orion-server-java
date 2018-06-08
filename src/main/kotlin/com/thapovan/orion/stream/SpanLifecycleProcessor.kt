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
import org.apache.kafka.streams.kstream.SessionWindows
import org.apache.kafka.streams.kstream.TimeWindows
import kotlin.math.min

object SpanLifecycleProcessor {
    fun buildGraph(streamsBuilder: StreamsBuilder, protoSpanStartStopEventStream: KStream<String, Span>) {
        val aggTypeToken = object : TypeToken<SpanNode>() {}.type
        val gson = GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .serializeNulls()
            .create()

        val startStopSpan = protoSpanStartStopEventStream
            .mapValues {
                val startTime = if(it.hasStartEvent()) it.timestamp else 0
                val endTime = if(it.hasEndEvent()) it.timestamp else 0
                val spanStartId = if(it.hasStartEvent()) it.internalSpanRefNumber else 0
                val spanNode = SpanNode(it.spanId,
                    it.serviceName,
                    it.parentSpanId,
                    startTime,
                    endTime,traceId = it.traceContext.traceId,
                    traceName = it.traceContext.traceName,
                    start_id = spanStartId)
                gson.toJson(spanNode,aggTypeToken).toByteArray()
            }
            .groupBy { key, value ->
                val parts = key.split("_")
                return@groupBy "${parts[0]}_${parts[1]}"
            }
            .windowedBy(SessionWindows.with(KafkaStream.WINDOW_DURATION_MS))
            .reduce { value1, value2 ->
                println("reducing span lifecycle")
                val spanNode1 = gson.fromJson<SpanNode>(String(value1), aggTypeToken)
                val spanNode2 = gson.fromJson<SpanNode>(String(value2), aggTypeToken)
                val newSpan = SpanNode(
                    spanNode1.spanId,
                    spanNode1.serviceName ?: spanNode2.serviceName,
                    spanNode1.parentId ?: spanNode2.parentId,
                    if (spanNode1.startTime == 0L) spanNode2.startTime else spanNode1.startTime,
                    if (spanNode1.endTime == 0L) spanNode2.endTime else spanNode1.endTime,
                    traceId = spanNode1.traceId ?: spanNode2.traceId,
                    traceName = spanNode1.traceName ?: spanNode2.traceName,
                    start_id = if(spanNode1.start_id == 0L) spanNode2.start_id else spanNode1.start_id
                )
                gson.toJson(newSpan, aggTypeToken).toByteArray()
            }
            .toStream()
            .selectKey { key, _ -> key.key() }
        startStopSpan.foreach { key, value -> println(String(value)) }

        startStopSpan.to("span-start-stop")
    }
}