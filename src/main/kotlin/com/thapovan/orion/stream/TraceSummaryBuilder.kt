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
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.thapovan.orion.data.MetaDataObject
import com.thapovan.orion.data.SpanTree
import com.thapovan.orion.data.TraceSummary
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.TimeWindows
import kotlin.math.max
import kotlin.math.min

object TraceSummaryBuilder {
    fun buildGraph(
        streamsBuilder: StreamsBuilder,
        fatTraceObjectStream: KStream<String, ByteArray>,
        metaDataObject: KStream<String, MetaDataObject>
    ) {
        val gson = GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .serializeNulls()
            .create()

        val metadataType = object : TypeToken<MetaDataObject>() {}.type
        val traceSummaryType = object : TypeToken<TraceSummary>() {}.type
        val spanTreeType = object : TypeToken<SpanTree>() {}.type

        val metadataTraceStream = metaDataObject.selectKey { key, _ ->
            key.split("_")[0]
        }
        val traceSummaryTable = fatTraceObjectStream
            .map { key,value ->
                val spanTree = gson.fromJson<SpanTree>(String(value),spanTreeType)
                val servicesList: MutableList<String> = ArrayList()
                spanTree.spanMap.forEach { t, u ->
                    if("ROOT" != u.spanId && u.serviceName != null)
                        servicesList.add(u.serviceName ?: "")
                }
                var startTime = 0L
                var endTime = 0L
                if(spanTree.rootNode.children.size>0) {
                    startTime = spanTree.rootNode.children.first().startTime
                    endTime = spanTree.rootNode.children.last().endTime
                }
                val traceId = key
                val traceSummary = TraceSummary(traceId,
                    startTime,
                    endTime,
                    serviceNames = servicesList,
                    traceEventSummary = spanTree.traceEventSummary?: HashMap())
                KeyValue.pair(key,gson.toJson(traceSummary,traceSummaryType).toByteArray())
            }

        val metadata = metadataTraceStream
            .selectKey { key, _ ->
                key.split("_")[0]
            }
            .mapValues {
                gson.toJson(it).toByteArray()
            }

        val summaryStream = traceSummaryTable
            .leftJoin(
                metadata,
                { summaryBytes: ByteArray, metadataByte: ByteArray ->
                    val metadataObjectValue = if (metadataByte == null || metadataByte.size == 0) {
                        MetaDataObject("", 0, 0, "", "")
                    } else {
                        gson.fromJson<MetaDataObject>(String(metadataByte), metadataType)
                    }
                    if (metadataObjectValue.spanId.isBlank()) {
                        summaryBytes
                    }
                    val metadata = JsonParser().parse(metadataObjectValue.metadata)
                    val jsonObject = try {
                        metadata.asJsonObject
                    } catch (e: Throwable) {
                        null
                    }
                    val summary = if (summaryBytes == null || summaryBytes.size == 0) {
                        TraceSummary("")
                    } else {
                        gson.fromJson<TraceSummary>(String(summaryBytes), traceSummaryType)
                    }
                    try {
                        if (jsonObject != null) {
                            if (jsonObject.has("http")) {
                                val http = jsonObject.getAsJsonObject("http")
                                if (http.has("request")) {
                                    val request = http.getAsJsonObject("request")
                                    if (request.has("ip")) {
                                        summary.country = request.get("ip").asString
                                    }
                                    if (request.has("country")) {
                                        summary.ip = request.get("country").asString
                                    }
                                }
                            } else {
                                if (jsonObject.has("http.request.ip")) {
                                    summary.ip = jsonObject.get("http.request.ip").asString
                                }
                                if (jsonObject.has("http.request.country")) {
                                    summary.country = jsonObject.get("http.request.country").asString
                                }
                            }
                            if (jsonObject.has("user")) {
                                val user = jsonObject.getAsJsonObject("user")
                                if (user.has("id")) {
                                    summary.userId = user.get("id").asString
                                }
                                if (user.has("email")) {
                                    summary.email = user.get("email").asString
                                }
                            } else {
                                if (jsonObject.has("user.id")) {
                                    summary.userId = jsonObject.get("user.id").asString
                                }
                                if (jsonObject.has("user.email")) {
                                    summary.email = jsonObject.get("user.email").asString
                                }
                            }

                            if (jsonObject.has("orion.signal")) {
                                val orion = jsonObject.get("orion.signal").asString
                                when (orion) {
                                    "START_TRACE" -> {
                                        summary.start_trace_count++
                                        println("found start trace")
                                    }
                                    "END_TRACE" -> {
                                        summary.end_trace_count++
                                        println("found end trace")
                                    }
                                }
                            } else {
                                if (jsonObject.has("orion") && jsonObject.getAsJsonPrimitive("orion").isJsonObject) {
                                    val orion = jsonObject.getAsJsonObject("orion")
                                    if (orion.has("signal") && jsonObject.getAsJsonPrimitive("signal").isString) {
                                        val signal = orion.get("signal").asString
                                        when (signal) {
                                            "START_TRACE" -> {
                                                summary.start_trace_count++
                                                println("found start trace")
                                            }
                                            "END_TRACE" -> {
                                                summary.end_trace_count++
                                                println("found end trace")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }catch (t: Throwable) {

                    }
                    gson.toJson(summary, traceSummaryType).toByteArray()
                },
                JoinWindows.of(KafkaStream.WINDOW_DURATION_MS)
            )
            .groupByKey()
            .windowedBy(TimeWindows.of(KafkaStream.WINDOW_DURATION_MS))
            .aggregate({
                gson.toJson(TraceSummary(""), traceSummaryType).toByteArray()
            },
                { key: String, value: ByteArray, aggregate: ByteArray ->
                    val summary = gson.fromJson<TraceSummary>(String(value), traceSummaryType)
                    val intermediateSummary = gson.fromJson<TraceSummary>(String(aggregate), traceSummaryType)
                    val traceId = key
                    println("received summary startcount ${summary.start_trace_count} endcount ${summary.end_trace_count}")
                    println("received intermediate startcount ${intermediateSummary.start_trace_count} endcount ${intermediateSummary.end_trace_count}")
                    val startTime =
                        if (intermediateSummary.startTime == 0L) summary.startTime else if (summary.startTime != 0L && summary.startTime < intermediateSummary.startTime) {
                            summary.startTime
                        } else {
                            intermediateSummary.startTime
                        }

                    val endTime =
                        if (intermediateSummary.endTime == 0L) summary.endTime else if (summary.endTime != 0L && summary.endTime > intermediateSummary.endTime) {
                            summary.endTime
                        } else {
                            intermediateSummary.endTime
                        }
                    val email = if (summary.email.isNullOrBlank()) intermediateSummary.email else summary.email
                    val userId = if (summary.userId.isNullOrBlank()) intermediateSummary.userId else summary.userId
                    val servicesSet = HashSet<String>()
                    servicesSet.addAll(intermediateSummary.serviceNames)
                    servicesSet.addAll(summary.serviceNames)
                    val traceSummary: MutableMap<String,Int> = HashMap()
                    summary.traceEventSummary.forEach { t, u ->
                        val iU = intermediateSummary.traceEventSummary[t] ?: -1
                        if (t == "ANOMALY" && iU != -1) {
                            traceSummary[t] = min(iU,u)
                        } else {
                            traceSummary[t] = max(iU,u)
                        }
                    }
                    val services = servicesSet.toMutableList()
                    val country = if (summary.country.isNullOrBlank()) intermediateSummary.country else summary.country
                    val ip = if (summary.ip.isNullOrBlank()) intermediateSummary.ip else summary.ip
                    val startTraceCount = summary.start_trace_count+intermediateSummary.start_trace_count
                    val endTraceCount = summary.end_trace_count+intermediateSummary.end_trace_count
                    var traceIncomplete = false
                    if (startTraceCount == 0 || endTraceCount == 0 || startTraceCount != endTraceCount) {
                        traceIncomplete = true
                    }
                    val finalSummary = TraceSummary(traceId, startTime, endTime, email, userId, services, traceSummary,
                        country,
                        ip,
                        traceIncomplete = traceIncomplete,
                        start_trace_count = startTraceCount,
                        end_trace_count = endTraceCount)
                    gson.toJson(finalSummary, traceSummaryType).toByteArray()
                })
            .toStream()
            .selectKey { key, _ -> key.key() }

        summaryStream.foreach { _, value -> println(String(value)) }
        summaryStream.to("trace-summary-json")

    }
}