package com.thapovan.orion.util

import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.common.Cluster

class TraceIdPartitioner : Partitioner {
    override fun configure(configs: MutableMap<String, *>?) {

    }

    override fun close() {

    }

    override fun partition(
        topic: String?,
        key: Any?,
        keyBytes: ByteArray?,
        value: Any?,
        valueBytes: ByteArray?,
        cluster: Cluster?
    ): Int {
        val key = String(keyBytes?: byteArrayOf(0))
        val maxPartition = cluster?.partitionCountForTopic(topic ?: "") ?: 1
        return key[0].toInt().rem(maxPartition)
    }
}