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

package com.thapovan.orion.server

import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.util.JsonFormat
import com.thapovan.orion.proto.BulkRequest
import com.thapovan.orion.proto.ServerResponse
import com.thapovan.orion.proto.Span
import com.thapovan.orion.proto.UnaryRequest
import org.apache.logging.log4j.LogManager
import spark.Request
import spark.Response


object TracerHttpServerHandler {

    private val LOG = LogManager.getLogger(this.javaClass)
    private val successResponse: ServerResponse = ServerResponse.newBuilder().setSuccess(true).build()
    private val sucessResponseJSON = JsonFormat
        .printer()
        .omittingInsignificantWhitespace()
        .preservingProtoFieldNames().print(successResponse)
    private val failureResponsePartial: ServerResponse = ServerResponse.newBuilder().setSuccess(false).buildPartial()

    fun handleUnaryRequest(request: Request, response: Response): String {
        LOG.info("Entering request")
        val jsonBody = request.body()
        response.header("Content-Type", "application/json")
        try {
            val unaryRequestBuilder = UnaryRequest.newBuilder()
            JsonFormat.parser().usingTypeRegistry(JsonFormat.TypeRegistry.newBuilder()
                .add(UnaryRequest.getDescriptor())
                .add(Span.getDescriptor())
                .build()).merge(jsonBody, unaryRequestBuilder)
            val unaryRequest = unaryRequestBuilder.build()
            if (!unaryRequest.hasSpanData() && !unaryRequest.spanData.spanId.isNullOrEmpty()) {
                sendError(response, Exception("Span Data not found"))
            }
            KafkaProducer.pushSpanEvent(unaryRequest?.spanData!!)
            LOG.info("Published request to kafka")
            return sucessResponseJSON
        } catch (e: InvalidProtocolBufferException) {
            LOG.error("Error in uploadSpan", e)
            response.status(400)
            return sendError(response, Exception("Invalid request"))
        } catch (e: Throwable) {
            LOG.error("Error in uploadSpan", e)
            response.status(500)
            return sendError(response, e)
        }
    }

    fun handleBulkRequest(request: Request, response: Response): String {
        val jsonBody = request.body()
        response.header("Content-Type", "application/json")
        try {
            val bulkRequestBuilder = BulkRequest.newBuilder()
            JsonFormat.parser().ignoringUnknownFields().merge(jsonBody, bulkRequestBuilder)
            val bulkRequest = bulkRequestBuilder.build()
            bulkRequest?.spanDataList?.forEach {
                KafkaProducer.pushSpanEvent(it)
            }
            LOG.info("Published request to kafka")
            return sucessResponseJSON
        } catch (e: InvalidProtocolBufferException) {
            LOG.error("Error in uploadSpan", e)
            response.status(400)
            return sendError(response, Exception("Invalid request"))
        } catch (e: Throwable) {
            LOG.error("Error in uploadSpan", e)
            response.status(500)
            return sendError(response, e)
        }
    }

    fun sendError(response: Response, cause: Throwable): String {
        val failureResponse = failureResponsePartial.toBuilder().setMessage(cause.message).build()
        return  JsonFormat.printer().omittingInsignificantWhitespace().preservingProtoFieldNames().print(
                failureResponse)
    }
}