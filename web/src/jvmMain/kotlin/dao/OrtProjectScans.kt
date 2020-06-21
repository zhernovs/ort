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
import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.web.common.OrtProjectScan
import org.ossreviewtoolkit.web.common.OrtProjectScanStatus

object OrtProjectScans : IntIdTable() {
    val ortProject: Column<EntityID<Int>> = reference("ort_project_id", OrtProjects)
    val dateTime: Column<Long> = long("date_time")
    val revision: Column<String> = text("revision")
    val status: Column<OrtProjectScanStatus> = enumerationByName("status", 50, OrtProjectScanStatus::class)
}

class OrtProjectScanDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<OrtProjectScanDao>(OrtProjectScans)

    var ortProject by OrtProjectDao referencedOn OrtProjectScans.ortProject
    var dateTime by OrtProjectScans.dateTime
    var revision by OrtProjectScans.revision
    var status by OrtProjectScans.status

    val analyzerResults by AnalyzerResultDao referrersOn AnalyzerResults.ortProjectScan

    fun detached(): OrtProjectScan =
        OrtProjectScan(id.value, transaction { ortProject.id.value }, dateTime, revision, status)
}
