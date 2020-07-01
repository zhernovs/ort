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

import org.ossreviewtoolkit.web.common.WebScanResult

import react.*
import react.dom.*

interface ScanResultTableProps : RProps {
    var scanResults: List<WebScanResult>
}

class ScanResultTable(props: ScanResultTableProps) : RComponent<ScanResultTableProps, RState>(props) {
    override fun RBuilder.render() {
        table {
            thead {
                tr {
                    th { +"Package" }
                    th { +"Status" }
                }
            }

            tbody {
                props.scanResults.forEach { scanResult ->
                    tr {
                        key = scanResult.id.toString()

                        td { +scanResult.packageId }
                        td { +scanResult.status.name }
                    }
                }
            }
        }
    }
}

fun RBuilder.scanResultTable(handler: ScanResultTableProps.() -> Unit): ReactElement =
    child(ScanResultTable::class) {
        this.attrs(handler)
    }
