package com.thapovan.orion.stream

import com.thapovan.orion.server.KafkaProducer
import org.apache.kafka.streams.StreamsBuilder

class FootprintBuilder{

    companion object: TopologyBuilder {
        override fun buildGraph(streamsBuilder: StreamsBuilder) {
            val requestStream = streamsBuilder.stream<String,ByteArray>(KafkaProducer.REQUEST_TOPIC)
                .groupBy { key, value ->
                    key.split("_")[0]
                }
        }

    }

}