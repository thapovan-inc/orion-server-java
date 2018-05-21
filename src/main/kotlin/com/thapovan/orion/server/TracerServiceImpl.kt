package com.thapovan.orion.server

import com.thapovan.orion.proto.*
import io.grpc.stub.StreamObserver
import org.apache.logging.log4j.LogManager

internal class TracerServiceImpl: TracerGrpc.TracerImplBase() {

    private val LOG = LogManager.getLogger(TracerServiceImpl::class.java)

    override fun uploadSpan(request: UnaryRequest?, responseObserver: StreamObserver<ServerResponse>?) {
        KafkaProducer.pushSpanEvent(request?.spanData!!)
        LOG.info("Published request to kafka")
        val response = ServerResponse.newBuilder()
            .setSuccess(true)
            .setMessage("")
            .setCode("")
            .build()
        responseObserver?.onNext(response)
        responseObserver?.onCompleted()
    }

    override fun uploadSpanBulk(request: BulkRequest?, responseObserver: StreamObserver<ServerResponse>?) {
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