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

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import java.io.File

import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.utils.*
import org.ossreviewtoolkit.web.common.OrtProjectScan
import org.ossreviewtoolkit.web.common.OrtProjectScanStatus
import org.ossreviewtoolkit.web.common.ScanStatus
import org.ossreviewtoolkit.web.jvm.dao.AnalyzerRunDao
import org.ossreviewtoolkit.web.jvm.dao.AnalyzerRunsScanResults
import org.ossreviewtoolkit.web.jvm.dao.OrtProjectScanDao
import org.ossreviewtoolkit.web.jvm.dao.OrtProjectScans
import org.ossreviewtoolkit.web.jvm.dao.ScanResultDao
import org.ossreviewtoolkit.web.jvm.dao.ScanResults

/**
 * A thread that polls the database for queued [OrtProjectScan]s and runs the [Analyzer] if it finds one. This service
 * is a simple way to run the analyzer as part of the web application for demonstration purposes. It should later be
 * replaced with two different approaches:
 * * A standalone analyzer service that runs on a different machine than the web application for security reasons.
 * * Project run the analyzer on their own CI and push the analyzer results to the web application.
 */
class AnalyzerService : Thread("AnalyzerService") {
    companion object {
        private const val POLL_INTERVAL = 5000L
    }

    override fun run() {
        while (true) {
            sleep(POLL_INTERVAL)

            try {
                val scan = findScan()
                if (scan != null) {
                    val downloadResult = download(scan)
                    if (downloadResult.success) {
                        log.info { "Finished download of ${scan.detached()}." }
                        val analyzeResult = analyze(scan, downloadResult.downloadDir)
                        val analyzerRunDao = analyzeResult.analyzerRun
                        if (analyzeResult.success && analyzerRunDao != null) {
                            log.info { "Finished analysis of ${scan.detached()}." }
                            transaction { scan.status = OrtProjectScanStatus.SCANNING_DEPENDENCIES }
                            analyzerRunDao.analyzerRun.result.packages.forEach {
                                // TODO: Also schedule scans for projects!
                                scheduleScan(analyzerRunDao, it.pkg)
                            }
                        } else {
                            // TODO: Add analyzer result with error message.
                            log.warn { "Analysis of ${scan.detached()} failed: ${analyzeResult.message}" }
                            transaction { scan.status = OrtProjectScanStatus.FAILED }
                        }
                    } else {
                        // TODO: Add analyzer result with error message.
                        log.warn { "Download of ${scan.detached()} failed: ${downloadResult.message}." }
                        transaction { scan.status = OrtProjectScanStatus.FAILED }
                    }
                    // TODO: Enable deletion of download directory.
                    //downloadResult.downloadDir.safeDeleteRecursively()
                }
            } catch (e: Exception) {
                e.showStackTrace()
            }
        }
    }

    private fun findScan(): OrtProjectScanDao? =
        transaction {
            log.debug { "Searching for ORT project scan to analyze..." }

            OrtProjectScanDao.find { OrtProjectScans.status eq OrtProjectScanStatus.QUEUED }.limit(1).firstOrNull()
                .also {
                    if (it != null) {
                        log.debug { "Found ORT project scan ${it.id}." }
                        it.status = OrtProjectScanStatus.START_ANALYZING_DEPENDENCIES
                    } else {
                        log.debug { "No ORT project scan found." }
                    }
                }
        }

    private fun download(scan: OrtProjectScanDao): DownloadPhaseResult {
        log.info { "Starting download of $scan." }
        transaction { scan.status = OrtProjectScanStatus.DOWNLOADING_SOURCE_CODE }
        val downloadDir = ortDataDirectory.resolve("web/download/${scan.id}")
        if (downloadDir.exists()) {
            return DownloadPhaseResult(
                false,
                downloadDir,
                "Download directory ${downloadDir.absolutePath} already exists."
            )
        }

        downloadDir.safeMkdirs()

        val ortProject = transaction { scan.ortProject }

        val vcs = VcsInfo(
            type = VcsType(ortProject.type),
            url = ortProject.url,
            revision = scan.revision,
            path = ortProject.path
        )

        val pkg = Package.EMPTY.copy(
            vcs = vcs,
            vcsProcessed = vcs.normalize()
        )

        return try {
            val result = Downloader.downloadFromVcs(pkg, downloadDir, true)
            DownloadPhaseResult(true, result.downloadDirectory)
        } catch (e: DownloadException) {
            e.showStackTrace()
            DownloadPhaseResult(false, downloadDir, e.collectMessagesAsString())
        }
    }

    private fun analyze(scan: OrtProjectScanDao, downloadDir: File): AnalyzerPhaseResult {
        log.info { "Starting analysis of $scan." }
        transaction { scan.status = OrtProjectScanStatus.ANALYZING_DEPENDENCIES }

        val config = AnalyzerConfiguration(ignoreToolVersions = true, allowDynamicVersions = true)
        val analyzer = Analyzer(config)

        return try {
            val ortResult = analyzer.analyze(downloadDir.normalize())
            val analyzerRun = ortResult.analyzer

            if (analyzerRun != null) {
                val analyzerRunDao = transaction {
                    AnalyzerRunDao.new {
                        this.ortProjectScan = scan
                        this.analyzerRun = analyzerRun
                    }
                }
                AnalyzerPhaseResult(true, analyzerRunDao)
            } else {
                AnalyzerPhaseResult(false, null)
            }
        } catch (e: Exception) {
            e.showStackTrace()
            AnalyzerPhaseResult(false, null, e.collectMessagesAsString())
        }
    }

    private fun scheduleScan(analyzerRunDao: AnalyzerRunDao, pkg: Package) {
        val newScan = transaction {
            val existingResults = ScanResults
                .slice(ScanResults.id, ScanResults.packageId)
                .select { (ScanResults.packageId eq pkg.id.toCoordinates()) and (ScanResults.pkg eq pkg) }
                .toList()

            existingResults.forEach { resultRow ->
                AnalyzerRunsScanResults.insert {
                    it[analyzerRun] = analyzerRunDao.id
                    it[scanResult] = resultRow[ScanResults.id]
                }
            }

            if (existingResults.isEmpty()) {
                log.debug { "Scheduling scan for '${pkg.id.toCoordinates()}'." }
                ScanResultDao.new {
                    this.packageId = pkg.id
                    this.pkg = pkg
                    this.scanResult = null
                    this.status = ScanStatus.QUEUED
                }
            } else null
        }

        if (newScan != null) {
            transaction {
                AnalyzerRunsScanResults.insert {
                    it[analyzerRun] = analyzerRunDao.id
                    it[scanResult] = newScan.id
                }
            }
        }
    }

    private data class DownloadPhaseResult(
        val success: Boolean,
        val downloadDir: File,
        val message: String = ""
    )

    private data class AnalyzerPhaseResult(
        val success: Boolean,
        val analyzerRun: AnalyzerRunDao?,
        val message: String = ""
    )
}
