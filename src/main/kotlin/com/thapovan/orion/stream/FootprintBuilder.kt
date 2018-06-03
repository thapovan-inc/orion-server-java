package com.thapovan.orion.stream

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.protobuf.util.JsonFormat
import com.thapovan.orion.data.SpanNode
import com.thapovan.orion.data.SpanTree
import com.thapovan.orion.proto.Span
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.TimeWindows
import org.apache.logging.log4j.LogManager

class FootprintBuilder {

    companion object {

        //TODO: Must be configurable
        const val WINDOW_DURATION_MS = 5L * 60L * 1000L // 5 mins

        private var LOG = LogManager.getLogger(FootprintBuilder::class.java)

        fun buildGraph(streamsBuilder: StreamsBuilder, incomingRequestStream: KStream<String, ByteArray>) {
            val aggTypeToken = object : TypeToken<SpanTree>() {}.type
            val gson = GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .serializeNulls()
                .create()
            val incomingRequest = incomingRequestStream
            incomingRequest
                .foreach { key: String, bufBytes: ByteArray ->
                    val span = Span.parseFrom(bufBytes)
                    LOG.info("JSON span: {}", JsonFormat.printer().preservingProtoFieldNames().print(span))
                }
            val footPrintStream = incomingRequest
                .filter { key, value ->
                    key != null && value != null && value.size > 2
                }
                .groupBy { key, value ->
                    key.split("_")[0]
                }
                .windowedBy(TimeWindows.of(WINDOW_DURATION_MS))
                .aggregate(
                    {
                        gson.toJson(SpanTree(SpanNode("ROOT", null, null)), aggTypeToken).toByteArray()
                    },
                    { key, value, bValueAggregate ->
                        val footPrintTree =
                            Gson().fromJson<SpanTree>(String(bValueAggregate), aggTypeToken)
                        val tree = footPrintTree.rootNode
                        val span = Span.parseFrom(value)
                        val spanNode = SpanNode(
                            span.spanId,
                            if (span.serviceName.isNullOrEmpty()) null else span.serviceName,
                            if (span.parentSpanId.isNullOrEmpty()) null else span.parentSpanId,
                            span.timestamp
                        )
                        val existingSpanNode: SpanNode? = tree.getIfExists(spanNode)
                        if (existingSpanNode != null) { // -> denotes that we have seen this span already

                            // lets check if the service name exists. if not present in the existingSpan, and if present
                            // in the received span, lets update the existingSpan
                            existingSpanNode.serviceName =
                                    if (existingSpanNode.serviceName.isNullOrEmpty() && !spanNode.serviceName.isNullOrEmpty())
                                        spanNode.serviceName
                                    else
                                        existingSpanNode.serviceName
                            if (existingSpanNode.startTime == 0L || spanNode < existingSpanNode) {
                                existingSpanNode.startTime = span.timestamp
                            } else {
                                if(span.hasEndEvent()) {
                                    existingSpanNode.endTime = span.timestamp
                                }
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
                        } else {
                            // This is the first time we are seeing this span

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
                                    tree.addChild(spanNode)
                                    footPrintTree.registerSpan(newParentSpan)
                                    footPrintTree.registerSpan(spanNode)
                                }
                            } else {
                                // parent not present. Add this span to the top of the tree
                                tree.addChild(spanNode)
                                footPrintTree.registerSpan(spanNode)
                            }
                        }
                        Gson().toJson(footPrintTree, aggTypeToken).toByteArray()
                    },
                    Materialized.with(Serdes.String(), Serdes.ByteArray())
                )
                .toStream()
                .selectKey { key, value ->  key.key()}

            footPrintStream.to("trace-footprint")
            footPrintStream.foreach({ _, byteValue ->
                println(String(byteValue))
            })
        }

    }

}