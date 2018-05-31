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

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import spark.Request
import spark.Response
import spark.Spark

class TracerHttpServer {

    private val LOG: Logger = LogManager.getLogger(this.javaClass)

    @Throws(Exception::class)
    fun start(httpPort: Int) {
        Spark.port(httpPort)
        Spark.before("/*") { request: Request, response: Response ->
            response.header("Access-Control-Allow-Origin", request.headers("Origin") ?: "*")
        }
        Spark.options("/*") { request, response ->

            val accessControlRequestHeaders = request
                .headers("Access-Control-Request-Headers")
            if (accessControlRequestHeaders != null) {
                response.header(
                    "Access-Control-Allow-Headers",
                    accessControlRequestHeaders
                )
            }

            val accessControlRequestMethod = request
                .headers("Access-Control-Request-Method")
            if (accessControlRequestMethod != null) {
                response.header(
                    "Access-Control-Allow-Methods",
                    accessControlRequestMethod
                )
            }

            "OK"
        }

        Spark.post("/uploadSpan") { request, response ->
            TracerHttpServerHandler.handleUnaryRequest(request, response)
        }

        Spark.post("/bulkUpload") { request, response ->
            TracerHttpServerHandler.handleBulkRequest(request, response)
        }

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                val LOG = LogManager.getLogger(this@TracerHttpServer.javaClass)
                LOG.info("Tracer HTTP server shutting down since JVM is shutting down")
                this@TracerHttpServer.stop()
                LOG.info("Tracer HTTP Server has been stopped")
            }
        })

        LOG.info("Tracer HTTP Server started on port {}", httpPort)
    }

fun stop() {
    LOG.info("Tracer HTTP Server stopping")
    Spark.stop()
}
}