package com.thapovan.orion.util

import com.thapovan.orion.proto.Span
import com.thapovan.orion.proto.Trace
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.*

internal class SpanValidatorKtTest {

    @Test
    fun isTimeDiffMore() {
       val timeDiffMore = isHostClientTimeDiffExceeds(1527979906025000)
        assertEquals(timeDiffMore,false)
    }

    @Test
    fun validateUUID(){
        assertEquals(checkUUID(UUID.randomUUID().toString()),true)
    }

    @Test
    fun validateEmptyServiceSpan(){
        var spanBuilder = Span.newBuilder()

        spanBuilder.setServiceName("").setTraceContext(Trace.newBuilder().setTraceId(UUID.randomUUID().toString()))
                .setSpanId(UUID.randomUUID().toString())
                .setParentSpanId(UUID.randomUUID().toString());
        assertEquals(validateSpanMessage(spanBuilder.build()),"service name is invalid")
    }

    @Test
    fun validateInvalidUUIDSpan(){
        var spanBuilder = Span.newBuilder()

        spanBuilder.setServiceName("Testing").setTraceContext(Trace.newBuilder().setTraceId(UUID.randomUUID().toString()))
                .setSpanId("2786289672793676")
                .setParentSpanId(UUID.randomUUID().toString());
        assertEquals(validateSpanMessage(spanBuilder.build()),"span_id format is invalid")
    }
}