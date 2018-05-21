package com.thapovan.orion.server

import io.grpc.Server
import io.grpc.ServerBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.IOException

class TracerServer {

    private var server: Server? = null

    private val LOG: Logger = LogManager.getLogger(this.javaClass)

    @Throws(IOException::class)
    fun start(port: Int) {
        /* The port on which the server should run */
        server = ServerBuilder.forPort(port)
            .addService(TracerServiceImpl())
            .build()
            .start()
        LOG.info("Tracer Server started and listening on port {}",port)
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                val LOG = LogManager.getLogger(this@TracerServer.javaClass)
                LOG.info("Tracer Server shutting down since JVM is shutting down")
                this@TracerServer.stop()
                LOG.info("Tracer server has been stopped")
            }
        })
    }

    fun stop() {
        server?.shutdown()
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    @Throws(InterruptedException::class)
    fun blockUntilShutdown() {
        server?.awaitTermination()
    }

}