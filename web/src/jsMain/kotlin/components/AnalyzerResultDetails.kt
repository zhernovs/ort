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

import org.ossreviewtoolkit.web.common.WebAnalyzerResult

import react.*
import react.dom.*

interface AnalyzerResultDetailsProps : RProps {
    var analyzerResult: WebAnalyzerResult
}

class AnalyzerResultDetails(props: AnalyzerResultDetailsProps) :
    RComponent<AnalyzerResultDetailsProps, RState>(props) {
    override fun RBuilder.render() {
        val analyzerResult = props.analyzerResult

        analyzerResult.projects.forEach { project ->
            h3 { +"Project: ${project.id}" }

            table {
                thead {
                    tr {
                        th { +"Package" }
                        th { +"Scan Status" }
                    }
                }

                tbody {
                    project.dependencies.forEach { (pkg, status) ->
                        tr {
                            td { +pkg }
                            td { +(status?.name ?: "UNKNOWN") }
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.analyzerResultDetails(handler: AnalyzerResultDetailsProps.() -> Unit): ReactElement {
    return child(AnalyzerResultDetails::class) {
        this.attrs(handler)
    }
}
