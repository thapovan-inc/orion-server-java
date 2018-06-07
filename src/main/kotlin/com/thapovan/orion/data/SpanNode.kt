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

data class SpanNode(
    @Expose(serialize = true, deserialize = true) var spanId: String,
    @Expose(serialize = true, deserialize = true) var serviceName: String? = null,
    @Expose(serialize = true, deserialize = true) var parentId: String? = null,
    @Expose(serialize = true, deserialize = true) var startTime: Long = 0,
    @Expose(serialize = true, deserialize = true) var endTime: Long = 0,
    @Expose(serialize = true, deserialize = true) var logSummary: MutableMap<String, Int> = HashMap(),
    @Expose(serialize = true, deserialize = true) var children: MutableList<SpanNode> = ArrayList(),
    @Expose(serialize = true, deserialize = true) val events: MutableList<LogObject> = ArrayList(),
    @Expose(serialize = true, deserialize = true) var traceId: String? = null,
    @Expose(serialize = true, deserialize = true) var traceName: String? = null,
    @Expose(serialize = true, deserialize = true) var start_id: Long = 0
) : Comparable<SpanNode> {

    override fun compareTo(other: SpanNode): Int {
        return startTime.compareTo(other.startTime)
    }

    override fun equals(other: Any?): Boolean {
        if (other is SpanNode) {
            return this.spanId == other.spanId
        }
        return false
    }

    fun getIfExists(spanNode: SpanNode): SpanNode? {
        if (children.contains(spanNode)) {
            return children[children.indexOf(spanNode)]
        }
        children.forEach {
            val value = it.getIfExists(spanNode)
            if (value != null) {
                return value
            }
        }
        return null
    }

    fun getCompactClone(): SpanNode {
        return SpanNode(spanId, serviceName, parentId, startTime, endTime, logSummary, start_id = start_id)
    }

    fun addChild(spanNode: SpanNode) {
        spanNode.parentId = spanId
        children.add(spanNode)
        children.sort()
    }

    fun updateLogSummary() {

        var START = 0
        var STOP = 0
        var DEBUG = 0
        var INFO = 0
        var WARN = 0
        var ERROR = 0
        var CRITICAL = 0
        var START_TRACE = 0
        var END_TRACE = 0

        events.forEach {
            when (it.logLevel) {
                "START" -> {
                    if(it.metadata?.isJsonObject == true) {
                        val metaObject = it.metadata?.asJsonObject
                        if (metaObject.has("orion")) {
                            val orion = metaObject.get("orion")
                            if (orion.isJsonObject && orion.asJsonObject.has("signal")) {
                                val signal = orion.asJsonObject.get("signal").asString
                                when(signal) {
                                    "START_TRACE" -> {
                                        START_TRACE++
                                    }
                                    "END_TRACE" -> {
                                        END_TRACE++
                                    }
                                }
                            }
                        }
                    }
                    START++
                }
                "STOP" -> STOP++
                "DEBUG" -> DEBUG++
                "INFO" -> INFO++
                "WARN" -> WARN++
                "ERROR" -> ERROR++
                "CRITICAL" -> CRITICAL++
            }
        }
        logSummary.clear()
        if(START == 0 || (STOP != START)) {
            ERROR++
        }

        logSummary["START"] = START
        logSummary["STOP"] = STOP
        logSummary["DEBUG"] = DEBUG
        logSummary["INFO"] = INFO
        logSummary["WARN"] = WARN
        logSummary["ERROR"] = ERROR
        logSummary["CRITICAL"] = CRITICAL
        if (START_TRACE > 0) {
            logSummary["START_TRACE"] = START_TRACE
        }
        if (END_TRACE > 0) {
            logSummary["END_TRACE"] = END_TRACE
        }

    }
}