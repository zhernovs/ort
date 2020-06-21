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

import org.ossreviewtoolkit.web.common.OrtProject

import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.*
import react.router.dom.routeLink

interface OrtProjectTableProps : RProps {
    var ortProjects: List<OrtProject>
}

class OrtProjectTable(props: OrtProjectTableProps) : RComponent<OrtProjectTableProps, RState>(props) {
    override fun RBuilder.render() {
        table {
            thead {
                tr {
                    th { +"Name" }
                    th { +"Type" }
                    th { +"URL" }
                    th { +"Path" }
                    th { +"" }
                }
            }

            tbody {
                props.ortProjects.forEach { ortProject ->
                    tr {
                        key = ortProject.id.toString()

                        td { +ortProject.name }
                        td { +ortProject.type }
                        td { +ortProject.url }
                        td { +ortProject.path }
                        td { routeLink("ortProject/${ortProject.id}") { +"Details" } }
                    }
                }
            }
        }
    }
}

fun RBuilder.ortProjectTable(handler: OrtProjectTableProps.() -> Unit): ReactElement =
    child(OrtProjectTable::class) {
        this.attrs(handler)
    }
