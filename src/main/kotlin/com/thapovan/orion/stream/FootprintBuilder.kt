package com.thapovan.orion.stream

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.protobuf.util.JsonFormat
import com.thapovan.orion.proto.Span
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.SessionWindows
import org.apache.logging.log4j.LogManager

class FootprintBuilder{

    companion object: TopologyBuilder {
        private var LOG = LogManager.getLogger(FootprintBuilder::class.java)

        override fun buildGraph(streamsBuilder: StreamsBuilder) {
            val aggTypeToken = object : TypeToken<List<Span>>() {}.type
            val incomingRequest = streamsBuilder.stream<String,ByteArray>("incoming-request")
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
                .windowedBy(SessionWindows.with(5*1000))
                .aggregate (
                    {
                        Gson().toJson(ArrayList<Span>(), aggTypeToken).toByteArray()
                    },
                    {
                            key, value, bValueAggregate ->
                            val valueAggregate =
                                Gson().fromJson<MutableList<Span>>(String(bValueAggregate), aggTypeToken)
                            valueAggregate.add(Span.parseFrom(value))
                            valueAggregate.sortBy {
                                it.timestamp
                            }
                            Gson().toJson(valueAggregate, aggTypeToken).toByteArray()
                    },
                    {
                        aggKey: String?, aggOne: ByteArray, aggTwo: ByteArray ->
                            val arr1 = Gson().fromJson<MutableList<Span>>(String(aggOne),aggTypeToken)
                            val arr2 = Gson().fromJson<MutableList<Span>>(String(aggTwo),aggTypeToken)
                            arr1.addAll(arr2)
                        arr1.sortBy {
                                it.timestamp
                            }
                        Gson().toJson(arr1, aggTypeToken).toByteArray()
                    }
                )
                .toStream()
                .foreach({
                    _,byteValue ->
                    val value = Gson().fromJson<MutableList<Span>>(String(byteValue),aggTypeToken)
                    run {
                        val traceID = value[0].traceContext.traceId
                        val spanSet = HashSet<String>()
                        val array = ArrayList<Span>()
                        value.forEach {
                            if (spanSet.contains(it.spanId)) {
                                return@forEach
                            } else {
                                spanSet.add(it.spanId)
                                array.add(it)
                            }
                        }
                        val serviceNameFootPrint = StringBuilder()
                        val spanIDMap = HashMap<String,String>()
                        value.filter {
                            (it.hasStartEvent() || it.hasEndEvent()) && it.serviceName.isNotEmpty()
                        }
                        .forEach {
                            serviceNameFootPrint.append(it.serviceName)
                            spanIDMap[it.serviceName] = it.spanId
                            serviceNameFootPrint.append("->")
                        }
                        LOG.info("Service Footprint for traceID {}: Start->{}End", traceID, serviceNameFootPrint.toString())
                        LOG.info(Gson().toJson(spanIDMap))
                    }
                })
        }

    }

}