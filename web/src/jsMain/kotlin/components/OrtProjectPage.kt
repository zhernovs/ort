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

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

import org.ossreviewtoolkit.web.common.OrtProject
import org.ossreviewtoolkit.web.js.Api

import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.*
import react.router.dom.routeLink
import react.setState

import styled.*

interface OrtProjectPageProps : RProps {
    var ortProjectId: Int
}

interface OrtProjectPageState : RState {
    var isUpdating: Boolean
    var ortProject: OrtProject?
}

class OrtProjectPage(props: OrtProjectPageProps) :
    RComponent<OrtProjectPageProps, OrtProjectPageState>(props) {
    override fun OrtProjectPageState.init(props: OrtProjectPageProps) {
        isUpdating = true
        ortProject = null

        updateOrtProject()
    }

    override fun RBuilder.render() {
        div {
            routeLink("/main") { +"Back to ORT project list" }

            val ortProject = state.ortProject

            when {
                state.isUpdating -> div("loader") {}
                ortProject == null -> styledH2 { +"Could not find ORT project with id '${props.ortProjectId}'." }
                else -> {
                    styledH1 { +"ORT Project: ${ortProject.name}" }

                    table {
                        tr {
                            td { +"URL" }
                            td { +ortProject.url }
                        }
                        tr {
                            td { +"Type" }
                            td { +ortProject.type }
                        }
                        tr {
                            td { +"Path" }
                            td { +ortProject.path }
                        }
                    }
                }
            }
        }
    }

    private fun updateOrtProject() {
        MainScope().launch {
            // TODO: Does not work if id is invalid, because of 404. Change ApiResult class to be generic and contain result? Or use try/catch?
            val ortProject = Api.fetchOrtProject(props.ortProjectId)
            setState {
                isUpdating = false
                this.ortProject = ortProject
            }
        }
    }
}

fun RBuilder.ortProjectPage(handler: OrtProjectPageProps.() -> Unit): ReactElement =
    child(OrtProjectPage::class) {
        this.attrs(handler)
    }
