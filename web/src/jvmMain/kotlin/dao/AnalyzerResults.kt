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

import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.jsonMapper

object AnalyzerResults : IntIdTable() {
    val ortProjectScan: Column<EntityID<Int>> = reference("ort_project_scan_id", OrtProjects)
    val analyzerResult: Column<AnalyzerRun> = jsonb("analyzer_result", AnalyzerRun::class, jsonMapper)
}

class AnalyzerResultDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AnalyzerResultDao>(AnalyzerResults)

    var ortProjectScan by OrtProjectScanDao referencedOn AnalyzerResults.ortProjectScan
    var analyzerResult by AnalyzerResults.analyzerResult
}
