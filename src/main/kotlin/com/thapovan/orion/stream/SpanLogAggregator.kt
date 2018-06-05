package com.thapovan.orion.stream

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.thapovan.orion.data.LogObject
import com.thapovan.orion.data.SpanNode
import com.thapovan.orion.proto.Span
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.TimeWindows

object SpanLogAggregator {
    fun buildGraph(
        streamsBuilder: StreamsBuilder,
        protoStartStopStream: KStream<String, Span>,
        protoLogEventStream: KStream<String, Span>
    ) {

        val jsonParser = JsonParser()
        val logTypeToken = object : TypeToken<LogObject>() {}.type
        val logArrTypeToken = object : TypeToken<MutableList<LogObject>>() {}.type
        val gson = GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .serializeNulls()
            .create()

        val logObjectStream = protoLogEventStream
            .mapValues {
                val metadataJson = try {
                    jsonParser.parse(it.logEvent.jsonString)
                } catch (e: Throwable) {
                    null
                }
                LogObject(
                    it.traceContext.traceId,
                    it.spanId,
                    it.logEvent.eventId,
                    it.logEvent.message,
                    it.logEvent.level.name,
                    metadataJson
                )
            }
            .mapValues {
                gson.toJson(it, logTypeToken).toByteArray()
            }
            .selectKey { key, _ ->
                val parts = key.split("_")
                return@selectKey "${parts[0]}_${parts[1]}"
            }

        val startStopEventStream = protoStartStopStream
            .mapValues {
                val eventID: Long
                val message: String
                val logLevel: String
                val metadata: String

                when {
                    it.hasStartEvent() -> {
                        eventID = it.startEvent.eventId
                        message = "Span Started"
                        logLevel = "START"
                        metadata = it.startEvent.jsonString
                    }
                    it.hasEndEvent() -> {
                        eventID = it.endEvent.eventId
                        message = "Span Ended"
                        logLevel = "STOP"
                        metadata = it.endEvent.jsonString
                    }
                    else -> {
                        return@mapValues null
                    }
                }
                val metadataJson = try {
                    jsonParser.parse(metadata)
                } catch (e: Throwable) {
                    null
                }
                LogObject(
                    it.traceContext.traceId,
                    it.spanId,
                    eventID,
                    message,
                    logLevel,
                    metadataJson
                )
            }
            .filter { _, value -> value != null }
            .mapValues {
                gson.toJson(it!!, logTypeToken).toByteArray()
            }
            .selectKey { key, _ ->
                val parts = key.split("_")
                return@selectKey "${parts[0]}_${parts[1]}"
            }

        val logArrStream = logObjectStream
            .groupByKey()
            .windowedBy(TimeWindows.of(KafkaStream.WINDOW_DURATION_MS))
            .aggregate(
                {
                    gson.toJson(ArrayList<LogObject>() as MutableList<LogObject>, logArrTypeToken).toByteArray()
                },
                { key: String?, value: ByteArray, aggregate: ByteArray ->
                    val newLogObject = gson.fromJson<LogObject>(String(value), logTypeToken)
                    val logArray = gson.fromJson<MutableList<LogObject>>(String(aggregate), logArrTypeToken)
                    val existingIndex = logArray.indexOf(newLogObject)
                    if (existingIndex > -1) {
                        logArray[existingIndex] = newLogObject
                    } else {
                        logArray.add(newLogObject)
                    }
                    logArray.sortBy {
                        it.eventId
                    }
                    gson.toJson(logArray, logArrTypeToken).toByteArray()
                },
                Materialized.with(Serdes.String(), Serdes.ByteArray())
            )
            .toStream()
            .selectKey { key, _ -> key.key() }
        val spanLogArrayStream = startStopEventStream
            .leftJoin(
                logArrStream,
                { logObjectByte: ByteArray?, logArrayByte: ByteArray? ->
                        val logArray = if (logArrayByte == null || logArrayByte.isEmpty()) {
                            ArrayList<LogObject>()
                        } else {
                            gson.fromJson<MutableList<LogObject>>(String(logArrayByte), logArrTypeToken)
                        }

                        val finalArray = if (logObjectByte == null || logObjectByte.isEmpty()) {
                            logArray
                        } else {
                            val logObject = gson.fromJson<LogObject>(String(logObjectByte), logTypeToken)
                            logArray.add(logObject)
                            logArray.sortBy {
                                it.eventId
                            }
                            logArray
                        }
                        gson.toJson(finalArray, logArrTypeToken).toByteArray()
                },
                JoinWindows.of(KafkaStream.WINDOW_DURATION_MS)
            )
            .groupByKey()
            .windowedBy(TimeWindows.of(KafkaStream.WINDOW_DURATION_MS))
            .aggregate(
                {
                    gson.toJson(ArrayList<LogObject>() as MutableList<LogObject>, logArrTypeToken).toByteArray()
                },
                {
                        key: String, aggregate1: ByteArray, aggregate2: ByteArray ->
                    val logArray1 = gson.fromJson<MutableList<LogObject>>(String(aggregate1),logArrTypeToken)
                    val logArray2 = gson.fromJson<MutableList<LogObject>>(String(aggregate2),logArrTypeToken)
                    val set = HashSet<LogObject>()
                    set.addAll(logArray1)
                    set.addAll(logArray2)
                    val finalList = set.toMutableList().sortedBy { it.eventId }
                    gson.toJson(finalList,logArrTypeToken).toByteArray()
                },
                Materialized.with(Serdes.String(), Serdes.ByteArray())
            )
            .toStream()
            .selectKey { key, _ ->
                key.key()
            }

        spanLogArrayStream
            .to("span-log-aggregated")
        spanLogArrayStream.foreach { _, value -> println(String(value)) }

    }
}