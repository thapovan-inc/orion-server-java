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