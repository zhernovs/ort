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

interface StartScanFormProps : RProps {
    var onSubmit: (String?) -> Unit
}

interface StartScanFormState : RState {
    var revision: String
    var canSubmit: Boolean
}

class StartScanForm(props: StartScanFormProps) : RComponent<StartScanFormProps, StartScanFormState>(props) {
    override fun StartScanFormState.init(props: StartScanFormProps) {
        revision = ""
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
                h2 { +"Start New Scan" }

                form {
                    attrs {
                        onSubmitFunction = { event ->
                            event.preventDefault()
                            props.onSubmit(state.revision)
                        }
                    }

                    div {
                        label { +"Revision:" }

                        input(InputType.text) {
                            attrs {
                                placeholder = "Revision"
                                onChangeFunction = { event -> updateForm(event.target) { revision = it } }
                            }
                        }
                    }

                    button(type = ButtonType.submit) {
                        attrs {
                            disabled = !state.canSubmit
                        }

                        +"Start New Scan"
                    }
                }
            }
        }
    }

    private fun updateForm(target: EventTarget?, block: StartScanFormState.(String) -> Unit) {
        setState {
            val value = when (target) {
                is HTMLInputElement -> target.value
                is HTMLSelectElement -> target.value
                else -> ""
            }
            block(value)
            canSubmit = revision.isNotBlank()
        }
    }
}

fun RBuilder.startScanForm(handler: StartScanFormProps.() -> Unit): ReactElement =
    child(StartScanForm::class) {
        this.attrs(handler)
    }
