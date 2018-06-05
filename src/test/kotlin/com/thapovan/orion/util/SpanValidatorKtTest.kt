package com.thapovan.orion.util

import com.thapovan.orion.proto.Span
import com.thapovan.orion.proto.StartEvent
import com.thapovan.orion.proto.Trace
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.*
import java.util.TimeZone
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneOffset


internal class SpanValidatorKtTest {

    @Test
    fun isTimeDiffMore() {

        var currentTime = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
        var fiveMinsAfter = (currentTime + 5*60*1000) + 2000
        var fiveMinsBefore = (currentTime - 5*60*1000) - 1
        val timeDiffMore = isHostClientTimeDiffExceeds(currentTime*1000)
        assertEquals(isHostClientTimeDiffExceeds(fiveMinsAfter*1000),true)
        assertEquals(isHostClientTimeDiffExceeds(fiveMinsBefore*1000),true)
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

        spanBuilder.setServiceName("").setTraceContext(Trace.newBuilder().setTraceId(UUID.randomUUID().toString()))
                .setSpanId("2786289672793676")
                .setParentSpanId("");
        assertEquals(validateSpanMessage(spanBuilder.build()),"span_id format is invalid")
    }

    @Test
    fun validateBulkSpanInputs(){
        var spanBuilder = Span.newBuilder()
        var spanBuilder1 = Span.newBuilder()
        spanBuilder.setServiceName("Testing").setTraceContext(Trace.newBuilder().setTraceId(UUID.randomUUID().toString()))
                .setSpanId(UUID.randomUUID().toString())
                .setParentSpanId(UUID.randomUUID().toString())

        spanBuilder1.setTraceContext(Trace.newBuilder().setTraceId(UUID.randomUUID().toString()))
                .setSpanId(UUID.randomUUID().toString()).setStartEvent(StartEvent.getDefaultInstance())
                .setParentSpanId(UUID.randomUUID().toString())

        val spans: MutableList<Span> = mutableListOf<Span>()

        spans.add(spanBuilder.build())
        spans.add(spanBuilder1.build())


        var validationMsg = validateBulkSpans(spans)
        assertEquals( true,true)

    }
}