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

import com.here.ort.model.Identifier
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.jsonMapper
import com.here.ort.model.yamlMapper
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.safeMkdirs
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

object CacheCleanup {

    const val LIMIT = 100000

    /**
     * A list of file paths to ignore.
     */
    val blacklist = listOf<String>(
            //"scan-results/Unmanaged/unknown/unknown/unknown/scan-results.yml"
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val artifactoryUrl = args[0]
        val artifactoryToken = args[1]
        val repository = args[2]

        val largestFiles = getLargestFiles(artifactoryUrl, artifactoryToken, repository).filter {
            it.first.startsWith("scan-results") && it.first.endsWith("scan-results.yml")
        }

        val downloadDir = File("/tmp/cache-cleanup")

        largestFiles.forEachIndexed { index, (file, size) ->
            println("Processing $file (${index + 1}/${largestFiles.size})...")
            if (file !in blacklist && file.startsWith("scan-results") && file.endsWith("scan-results.yml")) {
                val target = File(downloadDir, file)

                if (target.isFile && target.length() == size) {
                    println("Already downloaded $file...")
                } else {
                    target.delete()

                    println("Downloading $file...")
                    target.parentFile.safeMkdirs()
                    URL("$artifactoryUrl/$repository/$file").openStream().use {
                        it.copyTo(target.outputStream())
                    }
                    println("Download completed.")
                }

                val id = cleanupDuplicates(target)

                // TODO upload file
                val cachePath = "scan-results/${id.toPath()}/scan-results.yml"

                val request = Request.Builder()
                        .header("X-JFrog-Art-Api", artifactoryToken)
                        .put(OkHttpClientHelper.createRequestBody(target))
                        .url("$artifactoryUrl/$repository/$cachePath")
                        .build()

                try {
                    OkHttpClientHelper.execute(Main.HTTP_CACHE_PATH, request).use { response ->
                        (response.code() == HttpURLConnection.HTTP_CREATED).also {
                            println(
                                    if (it) {
                                        "Uploaded $cachePath to Artifactory cache."
                                    } else {
                                        "Could not upload $cachePath to Artifactory cache: ${response.code()} - " +
                                                response.message()
                                    }
                            )
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()

                    println("Could not upload $cachePath to Artifactory cache: ${e.message}")
                }
            } else {
                println("Ignoring $file...")
            }
        }
    }

    private fun getLargestFiles(artifactoryUrl: String, artifactoryToken: String, repository: String)
            : List<Pair<String, Long>> {
        val process = ProcessCapture(
                "curl",
                "-X",
                "POST",
                "-H",
                "Content-Type: text/plain",
                "-H",
                "X-JFrog-Art-Api:$artifactoryToken",
                "$artifactoryUrl/api/search/aql",
                "-d",
                "items.find({\"type\":\"file\", \"repo\":\"$repository\"}).sort({\"\$desc\":[\"size\"]}).limit($LIMIT)"
        )

        val json = jsonMapper.readTree(process.stdout())

        return json["results"].map { node ->
            Pair("${node["path"].asText()}/${node["name"].asText()}", node["size"].asLong()).also {
                println("${it.first}: ${it.second / (1024 * 1024)}MB")
            }
        }
    }

    private fun cleanupDuplicates(target: File): Identifier {
        printMemoryUsage()

        println("Running garbage collector...")
        System.gc()

        printMemoryUsage()

        println("Parsing ${target.invariantSeparatorsPath} (${target.length() / (1024 * 1024)}MB)...")
        val startTime = System.currentTimeMillis()
        val scanResultContainer = yamlMapper.readValue(target, ScanResultContainer::class.java)
        val endTime = System.currentTimeMillis()

        println("Parsing took ${((endTime - startTime) / 1000f).roundToInt()}s...")

        printMemoryUsage()

        println("Running garbage collector...")
        System.gc()

        printMemoryUsage()

        println("File contains ${scanResultContainer.results.size} scan results...")

        val results = filterIncompleteProvenance(scanResultContainer.results)

        println("Checking for duplicate scan results of '${scanResultContainer.id}'...")

        val uniques = mutableListOf<ScanResult>()
        val duplicates = mutableListOf<ScanResult>()

        results.forEach { scanResult ->
            if (scanResult !in duplicates) {
                uniques += scanResult

                val allScanResults = ArrayList(scanResultContainer.results)
                allScanResults.retainAll {
                    it.provenance.originalVcsInfo == scanResult.provenance.originalVcsInfo
                            && it.provenance.sourceArtifact == scanResult.provenance.sourceArtifact
                            && it.provenance.vcsInfo == scanResult.provenance.vcsInfo
                            && it.scanner.isCompatible(scanResult.scanner)
                }

                allScanResults.remove(scanResult)

                println("Found ${allScanResults.size} duplicates for provenance\n\t${scanResult.provenance}\nand " +
                        "scanner\n\t${scanResult.scanner}")

                duplicates += allScanResults
            }
        }

        println("Keeping ${uniques.size} unique scan results...")

        val cleanedScanResults = ScanResultContainer(scanResultContainer.id, uniques)
        yamlMapper.writeValue(target, cleanedScanResults)

        return scanResultContainer.id
    }

    private fun filterIncompleteProvenance(results: List<ScanResult>) =
            results.filter { scanResult ->
                if (scanResult.provenance.vcsInfo != null) {
                    when {
                        scanResult.provenance.vcsInfo?.resolvedRevision == null ->
                            false.also {
                                println("Removing scan result because resolved revision is missing: " +
                                        "${scanResult.provenance}")
                            }
                        scanResult.provenance.originalVcsInfo == null ->
                            false.also {
                                println("Removing scan result because original VCS info is missing: " +
                                        "${scanResult.provenance}")
                            }
                        else -> true
                    }
                } else {
                    true
                }
            }

    private fun printMemoryUsage() {
        val memoryUsage = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        println("Memory used: ${memoryUsage.used / (1024 * 1024)}/${memoryUsage.max / (1024 * 1024)}MB")
    }
}
