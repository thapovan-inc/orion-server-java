package com.thapovan.orion.data

import com.google.gson.JsonElement
import com.google.gson.annotations.Expose

data class LogObject(
    @Expose(serialize = true, deserialize = true) val traceId: String?,
    @Expose(serialize = true, deserialize = true) val spanId: String,
    @Expose(serialize = true, deserialize = true) val eventId: Long,
    @Expose(serialize = true, deserialize = true) val message: String,
    @Expose(serialize = true, deserialize = true) val logLevel: String,
    @Expose(serialize = true, deserialize = true) val metadata: JsonElement?
) {
    override fun equals(other: Any?): Boolean {
        return if (other is LogObject) {
            eventId == other.eventId && spanId == other.spanId && traceId == other.traceId
        } else {
            false
        }
    }
}