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
import org.ossreviewtoolkit.web.common.OrtProjectScan
import org.ossreviewtoolkit.web.common.WebAnalyzerResult
import org.ossreviewtoolkit.web.js.Api
import react.*
import react.dom.*
import styled.*

interface OrtProjectScanPageProps : RProps {
    var ortProjectId: Int
    var ortProjectScanId: Int
}

interface OrtProjectScanPageState : RState {
    var isUpdatingOrtProject: Boolean
    var isUpdatingOrtProjectScan: Boolean
    var isUpdatingAnalyzerResult: Boolean
    var ortProject: OrtProject?
    var ortProjectScan: OrtProjectScan?
    var analyzerResult: WebAnalyzerResult?
}

class OrtProjectScanPage(props: OrtProjectScanPageProps) :
    RComponent<OrtProjectScanPageProps, OrtProjectScanPageState>(props) {
    override fun OrtProjectScanPageState.init(props: OrtProjectScanPageProps) {
        isUpdatingOrtProject = true
        isUpdatingOrtProjectScan = true
        isUpdatingAnalyzerResult = true
        ortProject = null
        ortProjectScan = null
        analyzerResult = null

        updateOrtProject()
        updateRepositoryScan()
        updateAnalyzerResult()
    }

    override fun RBuilder.render() {
        div {
            //navLink("/repository/${props.ortProjectId}") { +"Back to repository details" }

            val ortProject = state.ortProject
            val ortProjectScan = state.ortProjectScan
            val analyzerResult = state.analyzerResult

            if (ortProject == null) {
                p { +"Loading repository." }
            } else {
                p { +"ORT Project: ${ortProject.name}" }
            }

            when {
                state.isUpdatingOrtProject -> div("loader") {}
                ortProject == null -> styledH2 { +"ORT project not found." }
                else -> styledH2 { +"ORT Project: ${ortProject.name}" }
            }

            when {
                state.isUpdatingOrtProjectScan -> div("loader") {}
                ortProjectScan == null -> p { +"ORT project scan not found." }
                else -> ortProjectScanDetails {
                    this.ortProjectScan = ortProjectScan
                }
            }

            when {
                state.isUpdatingAnalyzerResult -> div("loader") {}
                analyzerResult == null -> p { +"Analyzer result not found." }
                else -> analyzerResultDetails {
                    this.analyzerResult = analyzerResult
                }
            }
        }
    }

    private fun updateOrtProject() {
        MainScope().launch {
            val repository = Api.fetchOrtProject(props.ortProjectId)
            setState {
                this.isUpdatingOrtProject = false
                this.ortProject = repository
            }
        }
    }

    private fun updateRepositoryScan() {
        MainScope().launch {
            val repositoryScan = Api.fetchOrtProjectScan(props.ortProjectScanId)
            setState {
                this.isUpdatingOrtProjectScan = false
                this.ortProjectScan = repositoryScan
            }
        }
    }

    private fun updateAnalyzerResult() {
        MainScope().launch {
            val analyzerResult = Api.fetchAnalyzerResult(props.ortProjectScanId)
            setState {
                this.isUpdatingAnalyzerResult = false
                this.analyzerResult = analyzerResult
            }
        }
    }
}

fun RBuilder.ortProjectScanPage(handler: OrtProjectScanPageProps.() -> Unit): ReactElement {
    return child(OrtProjectScanPage::class) {
        this.attrs(handler)
    }
}
