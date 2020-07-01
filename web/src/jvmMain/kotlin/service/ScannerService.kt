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

package org.ossreviewtoolkit.web.jvm.service

import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.scanners.ScanCode
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.ortDataDirectory
import org.ossreviewtoolkit.utils.showStackTrace
import org.ossreviewtoolkit.web.common.ScanStatus
import org.ossreviewtoolkit.web.jvm.dao.ScanResultDao
import org.ossreviewtoolkit.web.jvm.dao.ScanResults

class ScannerService : Thread("ScannerService") {
    companion object {
        private const val POLL_INTERVAL = 5000L
    }

    override fun run() {
        while (true) {
            sleep(POLL_INTERVAL)

            try {
                val scan = findScan()
                if (scan != null) {
                    val result = scan(scan)
                    when {
                        result.success -> log.info { "Scan of '${scan.packageId.toCoordinates()}' finished." }
                        else -> log.warn { "Scan of '${scan.packageId.toCoordinates()}' failed: ${result.message}" }
                    }
                }
            } catch (e: Exception) {
                e.showStackTrace()
            }
        }
    }

    private fun findScan(): ScanResultDao? =
        transaction {
            log.debug { "Searching for queued scan job..." }

            ScanResultDao.find { ScanResults.status eq ScanStatus.QUEUED }.limit(1).firstOrNull()
                .also {
                    if (it != null) {
                        log.debug { "Found scan job ${it.id}." }
                        it.status = ScanStatus.SCANNING
                    } else {
                        log.debug { "No scan job found." }
                    }
                }
        }

    private fun scan(scan: ScanResultDao): ScanPhaseResult {
        log.info { "Starting scan of ${scan.pkg.id.toCoordinates()}." }

        val config = ScannerConfiguration()
        val scanner = ScanCode.Factory().create(config)

        val downloadDir = ortDataDirectory.resolve("web/download-pkg/${scan.id}")
        val outputDir = createTempDir("scancode")
        outputDir.deleteOnExit()

        return try {
            val scanResult = scanner.scanPackage(scanner.getDetails(), scan.pkg, outputDir, downloadDir)
            transaction {
                scan.scanResult = scanResult
                scan.status = ScanStatus.DONE
            }
            ScanPhaseResult(true)
        } catch (e: Exception) {
            e.showStackTrace()
            val message = "Could not scan '${scan.pkg.id.toCoordinates()}': ${e.collectMessagesAsString()}"
            log.warn { message }
            transaction { scan.status = ScanStatus.FAILED }
            ScanPhaseResult(false, message)
        }
    }

    private data class ScanPhaseResult(
        val success: Boolean,
        val message: String = ""
    )
}
