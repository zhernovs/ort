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

package org.ossreviewtoolkit.web.js.components

import org.ossreviewtoolkit.web.common.OrtProjectScan
import org.ossreviewtoolkit.web.js.formatDateTime

import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.*
import react.router.dom.*

interface OrtProjectScanTableProps : RProps {
    var ortProjectId: Int
    var ortProjectScans: List<OrtProjectScan>
}

class OrtProjectScanTable(props: OrtProjectScanTableProps) : RComponent<OrtProjectScanTableProps, RState>(props) {
    override fun RBuilder.render() {
        table {
            thead {
                tr {
                    th { +"Date" }
                    th { +"Revision" }
                    th { +"Status" }
                    th { +"" }
                }
            }

            tbody {
                props.ortProjectScans.forEach { ortProjectScan ->
                    tr {
                        key = ortProjectScan.id.toString()

                        td { +formatDateTime(ortProjectScan.dateTime) }
                        td { +ortProjectScan.revision }
                        td { +ortProjectScan.status.name }
                        td { routeLink("/ortProject/${props.ortProjectId}/scan/${ortProjectScan.id}") { +"Details" } }
                    }
                }
            }
        }
    }
}

fun RBuilder.ortProjectScanTable(handler: OrtProjectScanTableProps.() -> Unit): ReactElement =
    child(OrtProjectScanTable::class) {
        this.attrs(handler)
    }
