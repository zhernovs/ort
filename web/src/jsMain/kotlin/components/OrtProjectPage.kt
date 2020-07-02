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
import kotlinx.html.ButtonType
import kotlinx.html.js.onClickFunction

import org.ossreviewtoolkit.web.common.OrtProject
import org.ossreviewtoolkit.web.common.OrtProjectScan
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
    var isUpdatingScans: Boolean
    var ortProject: OrtProject?
    var ortProjectScans: List<OrtProjectScan>
    var showForm: Boolean
}

class OrtProjectPage(props: OrtProjectPageProps) :
    RComponent<OrtProjectPageProps, OrtProjectPageState>(props) {
    override fun OrtProjectPageState.init(props: OrtProjectPageProps) {
        isUpdating = true
        isUpdatingScans = true
        ortProject = null
        ortProjectScans = emptyList()
        showForm = false

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

                    button(type = ButtonType.button) {
                        attrs {
                            onClickFunction = { setState { showForm = true } }
                        }

                        +"Start new scan"
                    }

                    when {
                        state.isUpdatingScans -> div("loader") {}
                        state.ortProjectScans.isEmpty() -> styledH2 { +"No scans found." }
                        else -> ortProjectScanTable {
                            ortProjectId = props.ortProjectId
                            ortProjectScans = state.ortProjectScans
                        }
                    }

                    if (state.showForm) {
                        startScanForm {
                            onSubmit = ::startScan
                        }
                    }
                }
            }
        }
    }

    private fun updateOrtProject() {
        MainScope().launch {
            val ortProject = Api.fetchOrtProject(props.ortProjectId)
            setState {
                isUpdating = false
                this.ortProject = ortProject
            }
            updateOrtProjectScans()
        }
    }

    private fun updateOrtProjectScans() {
        MainScope().launch {
            val ortProjectScans = Api.fetchOrtProjectScans(props.ortProjectId)
            setState {
                isUpdatingScans = false
                this.ortProjectScans = ortProjectScans
            }
        }
    }

    private fun startScan(revision: String?) {
        setState { showForm = false }
        MainScope().launch {
            if (revision != null) {
                Api.startScan(props.ortProjectId, revision)

                updateOrtProjectScans()
            }
        }
    }
}

fun RBuilder.ortProjectPage(handler: OrtProjectPageProps.() -> Unit): ReactElement =
    child(OrtProjectPage::class) {
        this.attrs(handler)
    }
