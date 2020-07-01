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

import org.ossreviewtoolkit.web.common.WebScanResult
import org.ossreviewtoolkit.web.js.Api

import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.*
import react.setState

import styled.*

interface ScanResultListPageProps : RProps

interface ScanResultListPageState : RState {
    var isUpdating: Boolean
    var scanResults: List<WebScanResult>
}

class ScanResultListPage(props: ScanResultListPageProps) :
    RComponent<ScanResultListPageProps, ScanResultListPageState>(props) {
    override fun ScanResultListPageState.init(props: ScanResultListPageProps) {
        isUpdating = true
        scanResults = emptyList()

        updateScanResults()
    }

    override fun RBuilder.render() {
        div {
            styledH1 { +"Scan Results" }

            when {
                state.isUpdating -> div("loader") {}
                state.scanResults.isEmpty() -> styledH2 { +"No projects found." }
                else -> scanResultTable { scanResults = state.scanResults }
            }
        }
    }

    private fun updateScanResults() {
        MainScope().launch {
            val fetchedScanResults = Api.fetchScanResults()
            setState {
                isUpdating = false
                scanResults = fetchedScanResults
            }
        }
    }
}

fun RBuilder.scanResultListPage(handler: ScanResultListPageProps.() -> Unit): ReactElement =
    child(ScanResultListPage::class) {
        this.attrs(handler)
    }
