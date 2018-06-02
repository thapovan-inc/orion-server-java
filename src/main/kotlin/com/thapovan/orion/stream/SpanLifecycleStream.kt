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

package com.thapovan.orion.stream

import com.thapovan.orion.proto.Span
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Predicate

object SpanLifecycleStream {
    fun buildGraph(streamsBuilder: StreamsBuilder, incomingRequest: KStream<String, ByteArray>) {

        val startStopPredicate: Predicate<String,Span> = Predicate { key: String, value:Span -> value.hasStartEvent()||value.hasEndEvent() }
        val logEventPredicate: Predicate<String, Span> = Predicate { key, value ->  value.hasLogEvent() }

        val branches = incomingRequest
            .mapValues {
                return@mapValues Span.parseFrom(it)
            }
            .branch (
                startStopPredicate,
                logEventPredicate
            )
        val startStopStream = branches[0]
        val logStream = branches[1]
        startStopStream.to("span-start-stop")
        logStream.to("span-log")
    }
}