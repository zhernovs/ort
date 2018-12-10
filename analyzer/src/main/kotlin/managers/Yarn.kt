/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.analyzer.managers

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageJsonUtils
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.utils.CommandLineTool2
import com.here.ort.utils.OS

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver

import java.io.File

/**
 * The Yarn package manager for JavaScript, see https://www.yarnpkg.com/.
 */
class Yarn(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        NPM(analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Yarn>() {
        override val globsForDefinitionFiles = listOf("package.json")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                Yarn(analyzerConfig, repoConfig)
    }

    override val manager = object : CommandLineTool2("Yarn") {
        override val executable = if (OS.isWindows) "yarn.cmd" else "yarn"
        override val preferredVersion = Semver("1.12.1")
        override val requiredVersion = Requirement.buildNPM("1.3.* - 1.12.*")
    }

    override fun hasLockFile(projectDir: File) = PackageJsonUtils.hasYarnLockFile(projectDir)

    override fun mapDefinitionFiles(definitionFiles: List<File>) =
            PackageJsonUtils.mapDefinitionFilesForYarn(definitionFiles).toList()
}
