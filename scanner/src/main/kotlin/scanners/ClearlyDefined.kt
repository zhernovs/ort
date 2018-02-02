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

package com.here.ort.scanner.scanners

import ch.frankel.slf4k.info

import com.here.ort.model.Package
import com.here.ort.scanner.ClearlyDefinedService
import com.here.ort.scanner.Main
import com.here.ort.scanner.Scanner
import com.here.ort.utils.asTextOrEmpty
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log

import java.io.File

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody

import retrofit2.Retrofit

object ClearlyDefined : Scanner() {
    override fun scan(packages: List<Package>, outputDirectory: File, downloadDirectory: File?): Map<Package, Result> {

        require(Main.clearlyDefinedToken != null) {
            "Parameter --cd-token is required when using ClearlyDefined scanner."
        }

        require(Main.clearlyDefinedUrl != null) {
            "Parameter --cd-url is required when using ClearlyDefined scanner."
        }

        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${Main.clearlyDefinedToken}") // TODO get token from parameter
                .build()

            chain.proceed(request)
        }.build()

        val retrofit = Retrofit.Builder()
            .baseUrl(Main.clearlyDefinedUrl!!)
            .client(client)
            .build()

        val service = retrofit.create(ClearlyDefinedService::class.java)

        val packagesWithoutProject = packages.drop(1)

        val componentsForHarvest = "[\n${packagesWithoutProject.joinToString(separator = ",\n") {
            val namespace = if (it.id.namespace.isBlank()) "-" else it.id.namespace
            "  {\n    \"type\": \"package\",\n    \"url\": \"cd:/npm/npmjs/$namespace/${it.id.name}/${it.id.version}\"\n  }"
        }}\n]"

        log.info { "Enqueue components for harvesting:\n$componentsForHarvest" }
        val call = service.harvestData(RequestBody.create(MediaType.parse("application/json"), componentsForHarvest))
        val callResponse = call.execute()
        log.info { "Response: ${callResponse.isSuccessful} : ${callResponse.code()} : ${callResponse.message()}" }

        val componentsForStatus = "[\n${packagesWithoutProject.joinToString(separator = ",\n") {
            val namespace = if (it.id.namespace.isBlank()) "-" else it.id.namespace
            "\"npm/npmjs/$namespace/${it.id.name}/${it.id.version}\""
        }}\n]"

        while (true) {
            Thread.sleep(5000)

            log.info { "Request status for components:\n$componentsForStatus" }
            val statusResponse =
                service.harvestStatus(RequestBody.create(MediaType.parse("application/json"), componentsForStatus))
                    .execute()
            log.info { "Response: ${statusResponse.isSuccessful} : ${statusResponse.code()} : ${statusResponse.message()}" }
            val status = statusResponse.body()
            log.info { "Status is: ${status?.string()}" }

            if (statusResponse.code() == 200) {
                break
            }
        }

        return packagesWithoutProject.associateBy { it }.mapValues {
            val namespace = if (it.value.id.namespace.isBlank()) "-" else it.value.id.namespace
            val dataBody =
                service.getHarvestedData("npm", "npmjs", namespace, it.value.id.name, it.value.id.version)
            val rawData = dataBody.execute().body()?.string() ?: "{}"
            val rootNode = jsonMapper.readTree(rawData)
            val license = try {
                rootNode["scancode"]["2.2.1"]["licensed"]["license"].asTextOrEmpty()
            } catch (e: NullPointerException) {
                null
            }
            Result(setOf(license).filterNotNull().toSortedSet(), sortedSetOf())
        }
    }
}
