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

package org.ossreviewtoolkit.web.jvm.dao

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.web.common.ScanStatus

object ScanResults : IntIdTable() {
    val packageId: Column<String> = text("package_id")
    val pkg: Column<Package> = jsonb("package", Package::class, jsonMapper)
    val scanResult: Column<ScanResult?> = jsonb("scan_result", ScanResult::class, jsonMapper).nullable()
    val status: Column<ScanStatus> = enumerationByName("status", 50, ScanStatus::class)
}

class ScanResultDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ScanResultDao>(ScanResults)

    var packageId by ScanResults.packageId.transform({ it.toCoordinates() }, { Identifier(it) })
    var pkg by ScanResults.pkg
    var scanResult by ScanResults.scanResult
    var status by ScanResults.status

    val analyzerRuns by AnalyzerRunDao via AnalyzerRunsScanResults
}
