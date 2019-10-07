/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.test.readOrtResult

import java.io.ByteArrayOutputStream
import java.io.File

private fun generateReport(reporter: SpdxReporter, ortResult: OrtResult) =
    ByteArrayOutputStream().also { outputStream ->
        reporter.generateReport(
            outputStream,
            ReporterInput(ortResult)
        )
    }.toString("UTF-8")

class SpdxReporterTest : WordSpec({
    "SpdxReporter" should {
        "generate the correct JSON output" {
            val expectedText = File("src/funTest/assets/spdx.json").readText()
            val ortResult = readOrtResult("src/funTest/assets/NPM-is-windows-1.0.2-scan-result.json")

            val report = generateReport(SpdxJsonReporter(), ortResult)

            report shouldBe expectedText
        }

        "generate the correct XML output" {
            val expectedText = File("src/funTest/assets/spdx.xml").readText()
            val ortResult = readOrtResult("src/funTest/assets/NPM-is-windows-1.0.2-scan-result.json")

            val report = generateReport(SpdxXmlReporter(), ortResult)

            report shouldBe expectedText
        }

        "generate the correct YAML output" {
            val expectedText = File("src/funTest/assets/spdx.yml").readText()
            val ortResult = readOrtResult("src/funTest/assets/NPM-is-windows-1.0.2-scan-result.json")

            val report = generateReport(SpdxYamlReporter(), ortResult)

            report shouldBe expectedText
        }
    }
})
