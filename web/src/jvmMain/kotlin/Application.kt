/*
 * Copyright (C) 2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.web.jvm

import com.github.lamba92.ktor.features.SinglePageApplication

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import java.time.Instant

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.utils.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.ortDataDirectory
import org.ossreviewtoolkit.utils.printStackTrace
import org.ossreviewtoolkit.utils.showStackTrace
import org.ossreviewtoolkit.web.common.ApiResult
import org.ossreviewtoolkit.web.common.OrtProject
import org.ossreviewtoolkit.web.common.OrtProjectScanStatus
import org.ossreviewtoolkit.web.common.ScanStatus
import org.ossreviewtoolkit.web.jvm.dao.AnalyzerRuns
import org.ossreviewtoolkit.web.jvm.dao.AnalyzerRunsScanResults
import org.ossreviewtoolkit.web.jvm.dao.OrtProjectDao
import org.ossreviewtoolkit.web.jvm.dao.OrtProjectScanDao
import org.ossreviewtoolkit.web.jvm.dao.OrtProjectScans
import org.ossreviewtoolkit.web.jvm.dao.OrtProjects
import org.ossreviewtoolkit.web.jvm.dao.ScanResults
import org.ossreviewtoolkit.web.jvm.service.AnalyzerService
import org.ossreviewtoolkit.web.jvm.util.createSampleData

import org.postgresql.ds.PGSimpleDataSource

import org.slf4j.event.Level

internal const val TOOL_NAME = "web"

fun main() {
    printStackTrace = true

    val config = OrtConfiguration.load(configFile = ortDataDirectory.resolve("config/ort.conf"))
    val postgresConfig = config.web?.postgres

    require(postgresConfig != null) { "ORT config file is missing configuration for the PostgreSQL connection." }

    val dataSource = PGSimpleDataSource().apply {
        applicationName = "$ORT_FULL_NAME - $TOOL_NAME"
        setUrl(postgresConfig.url)
        user = postgresConfig.username
        password = postgresConfig.password
        currentSchema = postgresConfig.schema
        sslmode = postgresConfig.sslmode
        postgresConfig.sslcert?.let { sslcert = it }
        postgresConfig.sslkey?.let { sslkey = it }
        postgresConfig.sslrootcert?.let { sslrootcert = it }
    }

    Database.connect(dataSource)

    transaction {
        withDataBaseLock {
            SchemaUtils.createMissingTablesAndColumns(
                AnalyzerRuns,
                AnalyzerRunsScanResults,
                OrtProjects,
                OrtProjectScans,
                ScanResults
            )

            createSampleData()
        }
    }

    embeddedServer(Netty, 8080, watchPaths = listOf("ApplicationKt"), module = Application::module).start()

    AnalyzerService().start()
}

fun Application.module() {
    install(DefaultHeaders)

    install(CallLogging) {
        level = Level.INFO
    }

    install(ContentNegotiation) {
        json()
    }

    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError)
            throw cause
        }
    }

    install(SinglePageApplication) {
        defaultPage = "index.html"
        folderPath = "spa/"
        spaRoute = ""
        ignoreIfContains = Regex("/api")
    }

    routing {
        get("/") {
            call.respondRedirect("/main")
        }

        get("/api/ortProjects") {
            val ortProjects = transaction { OrtProjectDao.all().map { it.detached() } }

            call.respond(ortProjects)
        }

        get("/api/ortProjects/{id}") {
            val ortProjectId = call.parameters["id"]!!.toInt()
            val ortProject = transaction { OrtProjectDao.findById(ortProjectId)?.detached() }
            if (ortProject != null) {
                call.respond(ortProject)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/api/ortProjects/{id}/scans") {
            val ortProjectId = call.parameters["id"]!!.toInt()
            val scans = transaction {
                val ortProject = OrtProjectDao.findById(ortProjectId)
                ortProject?.scans?.map { ortProjectScan ->
                    if(ortProjectScan.status == OrtProjectScanStatus.SCANNING_DEPENDENCIES) {
                        // Updating the status here is not very efficient, especially for projects with many
                        // dependencies. This should be replaced with a more efficient approach.
                        // This approach could also give false results due to a race condition if the OrtProjectScan was
                        // just set to SCANNING_DEPENDENCIES but not all of the scans of the dependencies have been
                        // enqueued.
                        val done = ortProjectScan.analyzerRun.scanResults.all {
                            it.status in listOf(
                                ScanStatus.FAILED,
                                ScanStatus.DONE
                            )
                        }

                        if (done) {
                            ortProjectScan.status = OrtProjectScanStatus.DONE
                        }
                    }
                    ortProjectScan.detached()
                }.orEmpty()
            }
            call.respond(scans)
        }

        post("/api/ortProjects") {
            try {
                val ortProject = call.receive<OrtProject>()

                transaction {
                    OrtProjectDao.new {
                        name = ortProject.name
                        type = ortProject.type
                        url = ortProject.url
                        path = ortProject.path
                    }
                }

                call.respond(
                    HttpStatusCode.Created,
                    ApiResult(true, "Created ORT project: ${ortProject.name}")
                )
            } catch (e: Exception) {
                e.showStackTrace()
                call.respond(
                    HttpStatusCode.NotAcceptable,
                    ApiResult(false, "Could not create ORT project: ${e.message}")
                )
            }
        }

        post("/api/ortProjects/{id}/scans") {
            val ortProjectId = call.parameters["id"]!!.toInt()
            val revision = call.receive<String>()
            val ortProject = transaction { OrtProjectDao.findById(ortProjectId) }
            if (ortProject != null) {
                try {
                    transaction {
                        OrtProjectScanDao.new {
                            this.ortProject = ortProject
                            this.dateTime = Instant.now().toEpochMilli()
                            this.revision = revision
                            status = OrtProjectScanStatus.QUEUED
                        }
                    }
                    call.respond(HttpStatusCode.Created, ApiResult(true, "Started scan for revision: $revision"))
                } catch (e: Exception) {
                    e.showStackTrace()
                    call.respond(HttpStatusCode.NotAcceptable, ApiResult(false, "Could not start scan: ${e.message}"))
                }
            } else {
                call.respond(
                    HttpStatusCode.NotAcceptable,
                    ApiResult(false, "Could not find ORT project with id $ortProjectId.")
                )
            }
        }

        static("/static") {
            resource("web.js")
            resource("web.js.map")
            resources("static")
        }
    }
}
