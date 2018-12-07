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

package com.here.ort.utils

import ch.frankel.slf4k.*

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException

/**
 * An abstract class to be implement for functionality that is backed by a command line tool. The constructor gets a
 * human-readable [name] which can / should be different from the [executable], e.g. you may want to use "Mercurial"
 * instead of "hg" in user-facing output.
 */
abstract class CommandLineTool2(val name: String) {
    companion object {
        /**
         * A convenience version requirement that matches any version.
         */
        val ANY_VERSION = Requirement.buildNPM("*")
    }

    /**
     * The plain name of the executable file without any path.
     */
    abstract val executable: String

    /**
     * Mandatory arguments can be used e.g. if [executable] refers to a script interpreter and the command to run is a
     * specific script. The name of the script then as to be added to [mandatoryArguments].
     */
    open val mandatoryArguments = emptyList<String>()

    /**
     * Run this command with arguments [args] in the [workingDir] directory and the given [environment] variables. If
     * specified, use the [pathToExecutable] instead of looking it up in the PATH environment. If no [pathToExecutable]
     * is specified and no suitable [executable] version is not found in the PATH environment, try to bootstrap the
     * [preferredVersion], or throw a [NotImplementedError] if bootstrapping is not supported by this command.
     */
    fun run(vararg args: String, workingDir: File? = null, environment: Map<String, String> = emptyMap(),
            pathToExecutable: File? = null): ProcessCapture {
        val pathToUse = pathToExecutable ?: run {
            val pathFromEnvironment = getPathFromEnvironment(executable)
            if (pathFromEnvironment != null &&
                    requiredVersion.isSatisfiedBy(getVersion(pathToExecutable = pathFromEnvironment.parentFile))) {
                pathFromEnvironment.parentFile
            } else {
                log.info { "No suitable version of $name that satisfies $requiredVersion found in PATH." }

                bootstrappedPath ?: run {
                    // Try bootstrapping in any case, bootstrap() will throw
                    log.info { "Bootstrapping $name in version $preferredVersion..." }
                    bootstrap().also { bootstrappedPath = it }
                }
            }
        }

        val resolvedPath = pathToUse.resolve(executable).absolutePath
        val allArgs = mandatoryArguments.toTypedArray() + args
        return ProcessCapture(resolvedPath, *allArgs, workingDir = workingDir, environment = environment)
    }

    /**
     * Run this command in the [workingDir] directory with arguments [args].
     */
    fun run(workingDir: File?, vararg args: String) = run(*args, workingDir = workingDir)

    /**
     * The sarguments to pass to the command in order to gets it version.
     */
    open val versionArguments = listOf("--version")

    /**
     * Transform the version output of the command to a string that only contains the version.
     */
    open fun transformVersion(output: String) = output

    /**
     * Get the version of this command, optionally running in a specific [workingDir] (e.g. for commands that bootstrap
     * themselves) or using the executable in the [pathToExecutable].
     */
    fun getVersion(workingDir: File? = null, pathToExecutable: File? = null): Semver {
        val version = run(*versionArguments.toTypedArray(), workingDir = workingDir, pathToExecutable = pathToExecutable)

        // Some tools actually report the version to stderr, so try that as a fallback.
        val versionString = sequenceOf(version.stdout, version.stderr).map {
            transformVersion(it.trim())
        }.find {
            it.isNotBlank()
        }

        return Semver(versionString ?: "", Semver.SemverType.LOOSE)
    }

    /**
     * The preferred version of the command, which also is the version that would get bootstrapped.
     */
    abstract val preferredVersion: Semver

    /**
     * The required version (range) of the command which are acceptable for use. Defaults to the [preferredVersion].
     */
    open val requiredVersion by lazy { Requirement.build(preferredVersion)!! }

    /**
     * Check the command's version against the [requiredVersion].
     */
    fun checkVersion(workingDir: File? = null, pathToExecutable: File? = null, ignoreActualVersion: Boolean = false) {
        val actualVersion = getVersion(workingDir, pathToExecutable)

        if (!requiredVersion.isSatisfiedBy(actualVersion)) {
            val message = "Unsupported $name version $actualVersion does not fulfill $requiredVersion."
            if (ignoreActualVersion) {
                log.warn { "$message Still continuing because you chose to ignore the actual version." }
            } else {
                throw IOException(message)
            }
        }
    }

    /**
     * A flag to indicate whether this command can bootstrap itself.
     */
    open val canBootstrap = false

    /**
     * The path to the bootstrapped command, if it was bootstrapped.
     */
    private var bootstrappedPath: File? = null

    /**
     * Bootstrap the command, i.e. install it automatically if no suitable version is present on the system yet.
     */
    open fun bootstrap(): File = throw NotImplementedError()
}
