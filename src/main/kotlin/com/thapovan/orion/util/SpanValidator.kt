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

package com.thapovan.orion.util

import com.thapovan.orion.proto.Span
import java.time.LocalDateTime
import java.time.ZoneOffset

fun validateSpanMessage(span: Span?): String? {
    var errorMessage: String? = null
    if(span == null) {
        errorMessage = "Span object is null"
    }else if(span.spanId.isNullOrBlank()){
        errorMessage = "invalid span_id"
    }else if(!checkUUID(span.spanId)){
        errorMessage="span_id format is invalid"
    } else if(!checkUUID(span.traceContext.traceId)){
        errorMessage="trace_id is not valid"
    }else if(!span.parentSpanId.isNullOrBlank() && !checkUUID(span.parentSpanId)){
        errorMessage="parent span id format is invalid"
    }else if(!isHostClientTimeDiffExceeds(span.timestamp)){
        errorMessage="please verify system time"
    }else if(span.serviceName.isNullOrBlank()){
        errorMessage="service name is invalid"
    }
    return errorMessage
}

fun isHostClientTimeDiffExceeds(timestamp: Long): Boolean{
    var timestamp = timestamp/1000;

    var thresholdTime = 5*60*1000;
    var currentTime = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
    println("currentTime: "+currentTime)
    println("timestamp: "+timestamp)
    val timeDiff = Math.abs(timestamp - currentTime)
    println("timeDiff: "+timeDiff)
    return timeDiff > thresholdTime

}

fun checkUUID(UUID: String): Boolean{
    var isUUID: Boolean =false;

    val regex ="^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$".toRegex();
    return regex.containsMatchIn(UUID)
}