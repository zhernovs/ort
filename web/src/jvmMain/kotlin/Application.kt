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
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import org.slf4j.event.Level

fun main() {
    embeddedServer(Netty, 8080, watchPaths = listOf("ApplicationKt"), module = Application::module).start()
}

fun Application.module() {
    install(DefaultHeaders)

    install(CallLogging) {
        level = Level.INFO
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

        static("/static") {
            resource("web.js")
            resource("web.js.map")
            resources("static")
        }
    }
}
