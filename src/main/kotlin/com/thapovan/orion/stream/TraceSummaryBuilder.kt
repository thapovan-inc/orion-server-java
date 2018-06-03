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
import com.thapovan.orion.data.SpanNode
import com.thapovan.orion.data.TraceSummary
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.TimeWindows

object TraceSummaryBuilder {
    fun buildGraph(
        streamsBuilder: StreamsBuilder,
        spanStartStopStream: KStream<String, SpanNode>,
        metaDataObject: KStream<String, MetaDataObject>
    ) {
        val gson = GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .serializeNulls()
            .create()

        val metadataType = object : TypeToken<MetaDataObject>() {}.type
        val traceSummaryType = object : TypeToken<TraceSummary>() {}.type

        val metadataTraceStream = metaDataObject.selectKey { key, _ ->
            key.split("_")[0]
        }
        val traceSummaryTable = spanStartStopStream
            .selectKey { key, value -> key.split("_")[0] }
            .mapValues { value ->
                TraceSummary(
                    "", value.startTime, value.endTime, serviceNames = MutableList<String>(1,
                        { _ ->
                            value.serviceName ?: ""
                        })
                )
            }
            .mapValues {
                gson.toJson(it).toByteArray()
            }

        val summaryStream = metadataTraceStream
            .selectKey { key, _ ->
                key.split("_")[0]
            }
            .mapValues {
                gson.toJson(it).toByteArray()
            }
            .join(
                traceSummaryTable,
                { metadataByte: ByteArray, summaryBytes: ByteArray ->
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
                    if(jsonObject != null) {
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
                    val services = servicesSet.toMutableList()
                    val country = if (summary.country.isNullOrBlank()) intermediateSummary.country else summary.country
                    val ip = if (summary.ip.isNullOrBlank()) intermediateSummary.ip else summary.ip
                    val finalSummary = TraceSummary(traceId, startTime, endTime, email, userId, services, country, ip)
                    gson.toJson(finalSummary, traceSummaryType).toByteArray()
                })
            .toStream()
            .selectKey { key, _ -> key.key() }

        summaryStream.foreach { _, value -> println(String(value)) }
        summaryStream.to("trace-summary-json")

    }
}