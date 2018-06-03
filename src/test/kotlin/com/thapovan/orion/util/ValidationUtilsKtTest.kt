package com.thapovan.orion.util

import jdk.nashorn.internal.objects.NativeDate.getUTCMilliseconds
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.time.format.DateTimeFormatter

internal class ValidationUtilsKtTest {

    @Test
    fun isNullOrEmpty() {
        var emptyWithSpace = com.thapovan.orion.util.isNullOrEmpty("          ")
        var nullvalue = com.thapovan.orion.util.isNullOrEmpty(null)
        var falseCase = com.thapovan.orion.util.isNullOrEmpty("TestString")
        print(System.currentTimeMillis())
        assertEquals(true,emptyWithSpace)
        assertEquals(true,nullvalue)
        assertEquals(false,falseCase)
    }
}