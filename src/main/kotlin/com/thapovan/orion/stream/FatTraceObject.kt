package com.thapovan.orion.stream

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.thapovan.orion.data.LogObject
import com.thapovan.orion.data.SpanNode
import com.thapovan.orion.data.SpanTree
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.TimeWindows
import org.apache.kafka.streams.state.Stores

object FatTraceObject {

    fun buildGraph(
        streamsBuilder: StreamsBuilder,
        spanLogAggregateStream: KStream<String, ByteArray>,
        spanStartStop: KStream<String, ByteArray>
    ) {

        val aggTypeToken = object : TypeToken<SpanTree>() {}.type
        val spanNodeTypeToken = object : TypeToken<SpanNode>() {}.type
        val logArrTypeToken = object : TypeToken<MutableList<LogObject>>() {}.type
        val gson = GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .serializeNulls()
            .create()

        spanLogAggregateStream
            .join(
                spanStartStop,
                { spanLogArrayBytes: ByteArray, spanNodeBytes: ByteArray ->
                    val spanNode = if (spanNodeBytes == null || spanNodeBytes.size == 0) {
                        SpanNode("")
                    } else {
                        gson.fromJson<SpanNode>(String(spanNodeBytes), spanNodeTypeToken)
                    }
                    val spanLogArray = if (spanLogArrayBytes == null || spanLogArrayBytes.size == 0) {
                        ArrayList()
                    } else {
                        gson.fromJson<MutableList<LogObject>>(String(spanLogArrayBytes), logArrTypeToken)
                    }
                    val set = HashSet<LogObject>()
                    set.addAll(spanLogArray)
                    set.addAll(spanNode.events)
                    val finalList = set.toMutableList()
                    spanNode.events.clear()
                    spanNode.events.addAll(finalList)
                    spanNode.events.sortBy { it.eventId }
                    gson.toJson(spanNode, spanNodeTypeToken).toByteArray()
                },
                JoinWindows.of(KafkaStream.WINDOW_DURATION_MS)
            )
            .map { key, value ->
                val spanId = key.split("_")[1]
                val spanNode = gson.fromJson<SpanNode>(String(value), spanNodeTypeToken)
                spanNode.spanId = spanId
                return@map KeyValue<String, ByteArray>(
                    key.split("_")[0],
                    gson.toJson(spanNode, spanNodeTypeToken).toByteArray()
                )
            }
            .groupByKey()
//            .windowedBy(TimeWindows.of(KafkaStream.WINDOW_DURATION_MS)
//                .advanceBy(KafkaStream.WINDOW_SLIDE_DURATION_MS)
//                .until(2*KafkaStream.WINDOW_DURATION_MS))
            .aggregate(
                {
                    gson.toJson(SpanTree(SpanNode("ROOT", null, null)), aggTypeToken).toByteArray()
                },
                { key: String, spanNodeBytes: ByteArray, bValueAggregate: ByteArray ->
                    val footPrintTree =
                        gson.fromJson<SpanTree>(String(bValueAggregate), aggTypeToken)
                    val spanNode = gson.fromJson<SpanNode>(String(spanNodeBytes), spanNodeTypeToken)
                    val tree = footPrintTree.rootNode
                    val existingSpanNode: SpanNode? = tree.getIfExists(spanNode)
                    val traceName: String
                    val traceId: String
                    val finalSpanNode: SpanNode
                    if (existingSpanNode != null) { // -> denotes that we have seen this span already

                        val set = HashSet<LogObject>()
                        set.addAll(existingSpanNode.events)
                        set.addAll(spanNode.events)
                        val finalList = set.toMutableList()
                        existingSpanNode.start_id =
                                if (spanNode.start_id != 0L) spanNode.start_id else existingSpanNode.start_id
                        existingSpanNode.events.clear()
                        existingSpanNode.events.addAll(finalList)
                        existingSpanNode.events.sortBy { it.eventId }
                        existingSpanNode.updateLogSummary()

                        if (existingSpanNode.traceId.isNullOrBlank() && !spanNode.traceId.isNullOrBlank()) {
                            existingSpanNode.traceId = spanNode.traceId
                        }

                        if (existingSpanNode.traceName.isNullOrBlank() && !spanNode.traceName.isNullOrBlank()) {
                            existingSpanNode.traceName = spanNode.traceName
                        }

                        // lets check if the service name exists. if not present in the existingSpan, and if present
                        // in the received span, lets update the existingSpan
                        existingSpanNode.serviceName =
                                if (existingSpanNode.serviceName.isNullOrEmpty() && !spanNode.serviceName.isNullOrEmpty())
                                    spanNode.serviceName
                                else
                                    existingSpanNode.serviceName
                        if (existingSpanNode.startTime == 0L && spanNode.startTime != 0L) {
                            existingSpanNode.startTime = spanNode.startTime
                        }
                        if (existingSpanNode.endTime == 0L && spanNode.endTime != 0L) {
                            existingSpanNode.endTime = spanNode.endTime
                        }

                        if ((existingSpanNode.parentId.isNullOrEmpty() // -> means that the parent was not defined so far.. so this node resides at the top of the tree
                                    || existingSpanNode.parentId.equals("ROOT")) // -> means we are still waiting for the parent id to show up
                            && !spanNode.parentId.isNullOrEmpty()
                        ) {
                            // So far we didn't know the parent's ID; now we know who the parent is

                            // Lets search the tree if the parent is already present
                            val parentSpan = tree.getIfExists(SpanNode(spanNode.parentId!!, null, null))
                            if (parentSpan != null) { // -> the parent is already present in the tree
                                parentSpan.addChild(existingSpanNode) // -> add existingSpan to the parent
                                tree.children.remove(existingSpanNode)
                                footPrintTree.registerSpan(parentSpan)
                            } else {
                                // We haven't received any info about the parent span, we only know the parent's ID
                                // Lets create place holder for this new parent
                                val newParentSpan = SpanNode(spanNode.parentId!!, null, null)
                                newParentSpan.addChild(existingSpanNode)
                                tree.addChild(newParentSpan)
                                footPrintTree.registerSpan(newParentSpan)
                            }

                            tree.children.remove(existingSpanNode) // -> remove exisitingSpan from top of the tree
                        }
                        footPrintTree.registerSpan(existingSpanNode)
                        println("SpanID ${existingSpanNode.spanId} trace name ${existingSpanNode.traceName}")
                        traceName = existingSpanNode?.traceName ?: ""
                        traceId = existingSpanNode?.traceId ?: ""
                        finalSpanNode = existingSpanNode
                    } else {
                        // This is the first time we are seeing this span
                        spanNode.updateLogSummary()
                        // Check if the parent is present
                        if (!spanNode.parentId.isNullOrEmpty()) { // -> parent id is available
                            val parentSpan = tree.getIfExists(SpanNode(spanNode.parentId!!))
                            if (parentSpan != null) {
                                parentSpan.addChild(spanNode)
                                footPrintTree.registerSpan(parentSpan)
                                footPrintTree.registerSpan(spanNode)
                            } else {
                                val newParentSpan = SpanNode(spanNode.parentId!!)
                                newParentSpan.addChild(spanNode)
                                tree.addChild(newParentSpan)
                                footPrintTree.registerSpan(newParentSpan)
                                footPrintTree.registerSpan(spanNode)
                            }
                        } else {
                            // parent not present. Add this span to the top of the tree
                            tree.addChild(spanNode)
                            footPrintTree.registerSpan(spanNode)
                        }
                        println("SpanID ${spanNode.spanId} trace name ${spanNode.traceName}")
                        traceName = spanNode?.traceName ?: ""
                        traceId = spanNode?.traceId ?: ""
                        finalSpanNode = spanNode
                    }
                    val startTraceCount = finalSpanNode.logSummary["START_TRACE"] ?: 0
                    if (startTraceCount > 0) {
                        footPrintTree.startTime = when {
                            footPrintTree.startTime == 0L -> {
                                finalSpanNode.startTime
                            }
                            footPrintTree.startTime > finalSpanNode.startTime -> {
                                finalSpanNode.startTime
                            }
                            else -> {
                                footPrintTree.startTime
                            }
                        }
                    }

                    val stopTraceCount = finalSpanNode.logSummary["END_TRACE"] ?: 0
                    if (stopTraceCount > 0) {
                        footPrintTree.endTime = when {
                            footPrintTree.endTime < finalSpanNode.endTime -> {
                                finalSpanNode.endTime
                            }
                            else -> {
                                footPrintTree.endTime
                            }
                        }
                    }
                    footPrintTree.traceId =
                            if (footPrintTree?.traceId.isNullOrBlank()) traceId else footPrintTree?.traceId
                    footPrintTree.traceName =
                            if (footPrintTree?.traceName.isNullOrBlank()) traceName else footPrintTree?.traceName
                    footPrintTree.computeTraceSummary()
                    gson.toJson(footPrintTree, aggTypeToken).toByteArray()
                }, Materialized.`as`<String,ByteArray>(Stores.persistentKeyValueStore("fat-trace-store")))
            .mapValues {
                val footPrintTree = gson.fromJson<SpanTree>(String(it), aggTypeToken)
                footPrintTree.spanMap.forEach { _, u ->
                    val fullSpan = footPrintTree.rootNode.getIfExists(u)
                    val spanCopy: SpanNode
                    if (fullSpan != null) {
                        spanCopy = fullSpan.copy(children = ArrayList())
                        footPrintTree.spanList.add(spanCopy)
                    }
                }
                footPrintTree.spanList.sortBy {
                    it.start_id
                }
                gson.toJson(footPrintTree, aggTypeToken).toByteArray()
            }
            .toStream()
//            .selectKey { key, value -> key.key() }
            .to("fat-trace-object")
    }
}