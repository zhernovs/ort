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
import react.setState

import styled.*

interface OrtProjectListPageProps : RProps

interface OrtProjectListPageState : RState {
    var isUpdating: Boolean
    var ortProjects: List<OrtProject>
}

class OrtProjectListPage(props: OrtProjectListPageProps) :
    RComponent<OrtProjectListPageProps, OrtProjectListPageState>(props) {
    override fun OrtProjectListPageState.init(props: OrtProjectListPageProps) {
        isUpdating = true
        ortProjects = listOf()

        updateOrtProjects()
    }

    override fun RBuilder.render() {
        div {
            styledH1 { +"ORT Projects" }

            when {
                state.isUpdating -> div("loader") {}
                state.ortProjects.isEmpty() -> styledH2 { +"No projects found." }
                else -> ortProjectTable { ortProjects = state.ortProjects }
            }
        }
    }

    private fun updateOrtProjects() {
        MainScope().launch {
            val fetchedOrtProjects = Api.fetchOrtProjects()
            setState {
                isUpdating = false
                ortProjects = fetchedOrtProjects
            }
        }
    }
}

fun RBuilder.ortProjectListPage(handler: OrtProjectListPageProps.() -> Unit): ReactElement =
    child(OrtProjectListPage::class) {
        this.attrs(handler)
    }
