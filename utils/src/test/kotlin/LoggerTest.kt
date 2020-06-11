/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.system.captureStandardErr
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify

import org.apache.logging.log4j.Level

class Dummy

class LoggerTest : WordSpec({
    "A logger instance" should {
        "be shared between different instances of the same class" {
            val a = String().log
            val b = String().log

            a shouldBeSameInstanceAs b
        }

        "not be shared between instances of different classes" {
            val a = String().log
            val b = this.log

            a shouldNotBeSameInstanceAs b
        }
    }

    "Asking to log a statement only once" should {
        mockkStatic("org.ossreviewtoolkit.utils.LoggerKt")

        "show the message only once for the same instance and level" {
            val message = "This message should be logged only once."

            val stringLog = spyk<Dummy>()

            stringLog.logOnce(Level.WARN) { message }

            // This runs into a stack overflow.
            verify(exactly = 1) { stringLog.log.log(Level.WARN, message) }
        }

        "still show the message multiple times for different instances or levels" {
            val a = String()
            val message = "This message should be logged multiple times."

            val outputDifferentInstanceSameLevel = captureStandardErr {
                String().logOnce(Level.INFO) { message }
                String().logOnce(Level.INFO) { message }
            }.lines()

            val outputSameInstanceDifferentLevel = captureStandardErr {
                a.logOnce(Level.INFO) { message }
                a.logOnce(Level.WARN) { message }
            }.lines()

            outputDifferentInstanceSameLevel.filter { it == message }.size shouldBe 2
            outputSameInstanceDifferentLevel.filter { it == message }.size shouldBe 2
        }
    }
})
