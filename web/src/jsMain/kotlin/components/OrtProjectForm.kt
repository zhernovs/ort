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

import kotlin.dom.hasClass

import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onSubmitFunction

import org.ossreviewtoolkit.web.common.OrtProject
import org.ossreviewtoolkit.web.common.WebVcsType
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.EventTarget

import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.dom.*
import react.setState

interface OrtProjectFormProps : RProps {
    var onSubmit: (OrtProject?) -> Unit
}

interface OrtProjectFormState : RState {
    var name: String
    var type: String
    var url: String
    var path: String
    var canSubmit: Boolean
}

class OrtProjectForm(props: OrtProjectFormProps) : RComponent<OrtProjectFormProps, OrtProjectFormState>(props) {
    override fun OrtProjectFormState.init(props: OrtProjectFormProps) {
        name = ""
        type = WebVcsType.GIT.type
        url = ""
        path = ""
        canSubmit = false
    }

    override fun RBuilder.render() {
        div(classes = "modal") {
            attrs {
                onClickFunction = { event ->
                    val target = event.target
                    if (target is HTMLDivElement && target.hasClass("modal")) {
                        props.onSubmit(null)
                    }
                }
            }

            div("modal-content") {
                h2 { +"Create ORT Project" }

                form {
                    attrs {
                        onSubmitFunction = { event ->
                            event.preventDefault()
                            props.onSubmit(OrtProject(0, state.name, state.type, state.url, state.path))
                        }
                    }

                    div {
                        label { +"Name:" }

                        input(InputType.text) {
                            attrs {
                                placeholder = "Name"
                                onChangeFunction = { event -> updateForm(event.target) { name = it } }
                            }
                        }
                    }

                    div {
                        label { +"Type:" }

                        select {
                            attrs {
                                onChangeFunction = { event -> updateForm(event.target) { type = it } }
                                value = state.type
                            }

                            WebVcsType.values().forEach { vcsType ->
                                option {
                                    attrs.value = vcsType.type
                                    +vcsType.type
                                }
                            }
                        }
                    }

                    div {
                        label { +"URL:" }

                        input(InputType.text) {
                            attrs {
                                placeholder = "URL"
                                onChangeFunction = { event -> updateForm(event.target) { url = it } }
                            }
                        }
                    }

                    div {
                        label { +"Path:" }

                        input(InputType.text) {
                            attrs {
                                placeholder = "Path"
                                onChangeFunction = { event -> updateForm(event.target) { path = it } }
                            }
                        }
                    }

                    button(type = ButtonType.submit) {
                        attrs {
                            disabled = !state.canSubmit
                        }

                        +"Create ORT Project"
                    }
                }
            }
        }
    }

    private fun updateForm(target: EventTarget?, block: OrtProjectFormState.(String) -> Unit) {
        setState {
            val value = when (target) {
                is HTMLInputElement -> target.value
                is HTMLSelectElement -> target.value
                else -> ""
            }
            block(value)
            canSubmit = name.isNotBlank() && type.isNotBlank() && url.isNotBlank()
        }
    }
}

fun RBuilder.ortProjectForm(handler: OrtProjectFormProps.() -> Unit): ReactElement =
    child(OrtProjectForm::class) {
        this.attrs(handler)
    }
