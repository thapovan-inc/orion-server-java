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

import com.google.protobuf.util.JsonFormat
import com.thapovan.orion.proto.Span
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream

object JsonDebugStream {
    fun buildGraph(streamsBuilder: StreamsBuilder, incomingRequestStream: KStream<String, ByteArray>) {
        incomingRequestStream.mapValues {
            val span = Span.parseFrom(it)
            return@mapValues JsonFormat.printer().preservingProtoFieldNames().print(span).toByteArray()
        }
            .to("incoming-request-json")
    }
}