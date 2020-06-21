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

package org.ossreviewtoolkit.web.js

import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

import org.ossreviewtoolkit.web.common.ApiResult
import org.ossreviewtoolkit.web.common.OrtProject

object Api {
    // TODO: Use actual server URL instead of hardcoded localhost.
    private val API_URL = "http://localhost:8080/api"

    private val CLIENT = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }
    }

    suspend fun fetchOrtProject(id: Int): OrtProject? =
        try {
            CLIENT.get("$API_URL/ortProjects/$id")
        } catch (e: ClientRequestException) {
            null
        }

    suspend fun fetchOrtProjects(): List<OrtProject> = CLIENT.get("$API_URL/ortProjects")

    suspend fun createOrtProject(ortProject: OrtProject): ApiResult =
        CLIENT.post("$API_URL/ortProjects") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            body = ortProject
        }
}
