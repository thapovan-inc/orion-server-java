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
    private val spanMap = HashMap<String, SpanNode>()

    @Expose(serialize = true, deserialize = true)
    var rootNode: SpanNode

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
}