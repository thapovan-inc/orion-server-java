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

import com.thapovan.orion.proto.*
import io.grpc.stub.StreamObserver
import org.apache.logging.log4j.LogManager
import com.thapovan.orion.util.*
import java.util.stream.Collector

internal class TracerGrpcServiceImpl: TracerGrpc.TracerImplBase() {

    private val LOG = LogManager.getLogger(TracerGrpcServiceImpl::class.java)

    override fun uploadSpan(request: UnaryRequest?, responseObserver: StreamObserver<ServerResponse>?) {
        try {
            var response: ServerResponse? = null;
            var spanValidationMsg = validateSpanMessage(request?.spanData)
            if(spanValidationMsg.isNullOrEmpty()){
                KafkaProducer.pushSpanEvent(request?.spanData!!)
                LOG.info("Published request to kafka")
                 response = ServerResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("")
                        .setCode("")
                        .build()
                responseObserver?.onNext(response)
                responseObserver?.onCompleted()
            }else{
                 response = ServerResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage(spanValidationMsg)
                        .setCode("")
                        .build()

            }

            responseObserver?.onNext(response)
            responseObserver?.onCompleted()
        } catch (e: Throwable) {
            LOG.error("Error in uploadSpan",e)
        }
    }

    override fun uploadSpanBulk(request: BulkRequest?, responseObserver: StreamObserver<ServerResponse>?) {
        var spans = request?.spanDataList

        var validSpans = spans?.stream()?.filter{ span -> validateSpanMessage(span).isNullOrBlank()}


        request?.spanDataList?.forEach {
            KafkaProducer.pushSpanEvent(it)
        }
        val response = ServerResponse.newBuilder()
            .setSuccess(true)
            .setMessage("")
            .setCode("")
            .build()
        responseObserver?.onNext(response)
        responseObserver?.onCompleted()
    }

    override fun uploadSpanStream(responseObserver: StreamObserver<ServerResponse>?): StreamObserver<StreamRequest> {
        return super.uploadSpanStream(responseObserver)
    }
}