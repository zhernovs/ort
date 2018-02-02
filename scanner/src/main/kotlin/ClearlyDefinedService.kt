/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.scanner

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ClearlyDefinedService {

    @GET("harvest/{type}/{provider}/{namespace}/{name}/{revision}")
    fun getHarvestedData(
        @Path("type") type: String,
        @Path("provider") provider: String,
        @Path("namespace") namespace: String,
        @Path("name") name: String,
        @Path("revision") revision: String
    ): Call<List<String>>

    @POST("harvest")
    fun harvestData(@Body components: List<Component>): Call<Boolean>

    @POST("harvest/status")
    fun harvestStatus(@Body components: List<Component>): Call<Map<String, ComponentStatus>>
}

data class Component(
    /**
     * One of "package" or "source".
     */
    val type: String,

    /**
     * Url in the format "cd:/{type}/{provider}/{namespace}/{name}/{revision}". For example:
     * * cd:/npm/npmjs/-/redie/0.3.0
     * * cd:/git/github/microsoft/redie/194269b5b7010ad6f8dc4ef608c88128615031ca
     */
    val url: String
)

typealias ComponentStatus = Map<String, String>
