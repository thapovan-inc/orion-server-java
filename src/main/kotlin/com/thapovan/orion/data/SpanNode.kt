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
import java.util.*

class SpanNode (@Expose(serialize = true, deserialize = true) val spanId: String,
                @Expose(serialize = true, deserialize = true) var serviceName: String? = null,
                @Expose(serialize = true, deserialize = true) var parentId: String? = null,
                @Expose(serialize = true, deserialize = true) var startTime: Long = 0,
                @Expose(serialize = true, deserialize = true) val children: MutableList<SpanNode> = ArrayList()) : Comparable<SpanNode> {

    override fun compareTo(other: SpanNode): Int {
        return startTime.compareTo(other.startTime)
    }

    override fun equals(other: Any?): Boolean {
        if (other is SpanNode) {
            return this.spanId == other.spanId
        }
        return false
    }

    fun getIfExists(spanNode: SpanNode) : SpanNode? {
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
        return SpanNode(spanId,serviceName,parentId,startTime)
    }

    fun addChild(spanNode: SpanNode) {
        spanNode.parentId = spanId
        children.add(spanNode)
        children.sort()
    }
}