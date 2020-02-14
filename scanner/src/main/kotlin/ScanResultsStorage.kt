/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import com.here.ort.model.AccessStatistics
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScannerDetails
import com.here.ort.model.config.FileBasedStorageConfiguration
import com.here.ort.model.config.PostgresStorageConfiguration
import com.here.ort.scanner.storages.*
import com.here.ort.utils.ORT_NAME
import com.here.ort.utils.log
import com.here.ort.utils.storage.HttpFileStorage
import com.here.ort.utils.storage.LocalFileStorage

import java.lang.IllegalArgumentException
import java.sql.DriverManager
import java.util.Properties

/**
 * The abstract class that storage backends for scan results need to implement.
 */
abstract class ScanResultsStorage {
    /**
     * A companion object that allow to configure the globally used storage backend.
     */
    companion object {
        /**
         * The scan result storage in use. Needs to be set via the corresponding configure function.
         */
        var storage: ScanResultsStorage = NoStorage()

        /**
         * Given a [config] for a known storage backend, configure it as the current one.
         */
        fun configure(config: Any) {
            when (config) {
                is FileBasedStorageConfiguration -> configure(config)
                is PostgresStorageConfiguration -> configure(config)
                else -> throw IllegalArgumentException("Unsupported configuration type '$config'.")
            }
        }

        /**
         * Configure a [FileBasedStorage] as the current storage backend.
         */
        fun configure(config: FileBasedStorageConfiguration) {
            val backend = config.backend.createFileStorage()

            when (backend) {
                is HttpFileStorage -> log.info { "Using file based storage with HTTP backend '${backend.url}'." }
                is LocalFileStorage -> log.info {
                    "Using file based storage with local directory '${backend.directory.invariantSeparatorsPath}'."
                }
            }

            storage = FileBasedStorage(backend)
        }

        /**
         * Configure a [PostgresStorage] as the current storage backend.
         */
        fun configure(config: PostgresStorageConfiguration) {
            require(config.url.isNotBlank()) {
                "URL for PostgreSQL storage is missing."
            }

            require(config.schema.isNotBlank()) {
                "Database for PostgreSQL storage is missing."
            }

            require(config.username.isNotBlank()) {
                "Username for PostgreSQL storage is missing."
            }

            require(config.password.isNotBlank()) {
                "Password for PostgreSQL storage is missing."
            }

            val properties = Properties()
            properties["user"] = config.username
            properties["password"] = config.password
            properties["ApplicationName"] = "$ORT_NAME - $TOOL_NAME"

            val connection = DriverManager.getConnection(config.url, properties)

            storage = PostgresStorage(connection, config.schema).also { it.setupDatabase() }
        }
    }

    /**
     * The result of the [add] operation.
     */
    data class AddResult(
        /**
         * True, if the data was successfully added to the storage.
         */
        val success: Boolean,

        /**
         * Contains the error message if [success] is false.
         */
        val message: String? = null
    )

    /**
     * The name to refer to this storage implementation.
     */
    val name: String = javaClass.simpleName

    /**
     * The access statistics for the scan result storage.
     */
    val stats = AccessStatistics()

    /**
     * Read all [ScanResult]s for this [package][pkg] from the storage.
     */
    fun read(pkg: Package): ScanResultContainer =
        readFromStorage(pkg).also {
            stats.numReads.incrementAndGet()
            if (it.results.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }
        }

    /**
     * Read the [ScanResult]s for this [package][pkg] with matching [scannerDetails] from the storage.
     */
    fun read(pkg: Package, scannerDetails: ScannerDetails): ScanResultContainer =
        readFromStorage(pkg, scannerDetails).also {
            stats.numReads.incrementAndGet()
            if (it.results.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }
        }

    /**
     * Add a [scanResult] to the storage.
     */
    fun add(scanResult: ScanResult): AddResult {
        // Do not store empty scan results. It is likely that something went wrong when they were created, and if not,
        // it is cheap to re-create them.
        if (scanResult.summary.fileCount == 0) {
            val message =
                "Not storing scan result for '${scanResult.provenance.description}' because no files were scanned."
            log.info { message }

            return AddResult(false, message)
        }

        // Do not store scan results without raw result. The raw result can be set to null for other usages, but in the
        // storage it must never be null.
        if (scanResult.rawResult == null) {
            val message =
                "Not storing scan result for '${scanResult.provenance.description}' because the raw result is null."
            log.info { message }

            return AddResult(false, message)
        }

        // Do not store scan results without provenance information, because they cannot be assigned to the revision of
        // the package source code later.
        if (scanResult.provenance.sourceArtifact == null && scanResult.provenance.vcsInfo == null) {
            val message = "Not storing scan result for '${scanResult.provenance.description}' because no " +
                    "provenance information is available."
            log.info { message }

            return AddResult(false, message)
        }

        return addToStorage(scanResult)
    }

    protected abstract fun readFromStorage(pkg: Package): ScanResultContainer

    protected abstract fun readFromStorage(pkg: Package, scannerDetails: ScannerDetails): ScanResultContainer

    protected abstract fun addToStorage(scanResult: ScanResult): AddResult
}
