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

package com.thapovan.orion.data

import com.google.gson.annotations.Expose

class SpanTree {

    @Expose(serialize = true, deserialize = true)
    var traceId: String? = null

    @Expose(serialize = true, deserialize = true)
    var traceName: String? = null

    @Expose(serialize = true, deserialize = true)
    val spanMap = HashMap<String, SpanNode>()

    @Expose(serialize = true, deserialize = true)
    val traceEventSummary = HashMap<String,Int>()

    @Expose(serialize = true, deserialize = true)
    private val anomalySpans = ArrayList<String>()

    @Expose(serialize = true, deserialize = true)
    var rootNode: SpanNode

    @Expose(serialize = true, deserialize = true)
    var spanList: MutableList<SpanNode> = ArrayList()

    constructor(rootNode: SpanNode) {
        this.rootNode = rootNode
        this.spanMap["ROOT"] = rootNode
    }

    fun merge(spanTree: SpanTree) {
        TODO("Must work out the algorithm for merging two trees")
    }

    fun registerSpan(spanNode: SpanNode) {
        val compactSpan = spanNode.getCompactClone()
        this.spanMap[compactSpan.spanId] = compactSpan
    }

    fun computeTraceSummary() {
        var START = 0
        var STOP = 0
        var DEBUG = 0
        var INFO = 0
        var WARN = 0
        var ERROR = 0
        var CRITICAL = 0
        var ANOMALY = 0
        anomalySpans.clear()
        spanMap.values.forEach {
            val spanSummary = it.logSummary
            var startCount = 0
            var stopCount = 0
            spanSummary.entries.forEach {
                val key = it.key
                val value = it.value
                when (key) {
                    "START" -> {
                        START += value
                        startCount +=value
                    }
                    "STOP" -> {
                        STOP += value
                        stopCount += value
                    }
                    "DEBUG" -> DEBUG += value
                    "INFO" -> INFO += value
                    "WARN" -> WARN += value
                    "ERROR" -> ERROR += value
                    "CRITICAL" -> CRITICAL += value
                }
            }
            if(!it.spanId.equals("ROOT") && (
                        (it.startTime == 0L || it.endTime == 0L)
                        || (startCount != 1 || stopCount != 1))) {
                ANOMALY++
                anomalySpans.add(it.spanId)
            }
        }
        traceEventSummary.clear()

        traceEventSummary["START"] = START
        traceEventSummary["STOP"] = STOP
        traceEventSummary["DEBUG"] = DEBUG
        traceEventSummary["INFO"] = INFO
        traceEventSummary["WARN"] = WARN
        traceEventSummary["ERROR"] = ERROR
        traceEventSummary["CRITICAL"] = CRITICAL
        traceEventSummary["ANOMALY"] = ANOMALY
    }
}