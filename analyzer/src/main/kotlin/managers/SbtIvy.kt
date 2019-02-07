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

package com.here.ort.analyzer.managers

import ch.frankel.slf4k.*

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.MavenSupport
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.ResolutionResult
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.Os
import com.here.ort.utils.getCommonFileParent
import com.here.ort.utils.getUserHomeDirectory
import com.here.ort.utils.log
import com.here.ort.utils.suppressInput

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.repository.WorkspaceRepository

import java.io.File

/**
 * The sbt package manager for Scala, see https://www.scala-sbt.org/.
 */
class SbtIvy(
    name: String,
    analyzerRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) :
    PackageManager(name, analyzerRoot, analyzerConfig, repoConfig), CommandLineTool {
    companion object {
        // Sbt major versions that use individual configuration directories.
        private val MAJOR_VERSIONS = listOf("0.13", "1.0")

        // See the plugin project at https://github.com/jrudolph/sbt-dependency-graph.
        private const val DEPENDENCY_GRAPH_PLUGIN_NAME = "sbt-dependency-graph"
        private const val DEPENDENCY_GRAPH_PLUGIN_VERSION = "0.9.2"
        private const val DEPENDENCY_GRAPH_PLUGIN_DECLARATION = "addSbtPlugin(" +
                "\"net.virtual-void\" % \"$DEPENDENCY_GRAPH_PLUGIN_NAME\" % \"$DEPENDENCY_GRAPH_PLUGIN_VERSION\"" +
                ")"

        // Batch mode (which suppresses interactive prompts) is only supported on non-Windows, see
        // https://github.com/sbt/sbt-launcher-package/blob/d251388/src/universal/bin/sbt#L86.
        private val BATCH_MODE = if (!Os.isWindows) "-batch" else ""

        // See https://github.com/sbt/sbt/issues/2695.
        private val LOG_NO_FORMAT = "-Dsbt.log.noformat=true".let {
            if (Os.isWindows) {
                "\"$it\""
            } else {
                it
            }
        }

        // In the output of "sbt projects" the current project is indicated by an asterisk.
        private val PROJECT_REGEX = Regex("\\[info] \t ([ *]) (.+)")

        // The name of Ivy report file created by Sbt / the dependency graph plugin.
        private const val IVY_REPORT_FILE_NAME = "ivy-report.xsl"
    }

    class Factory : AbstractPackageManagerFactory<SbtIvy>("SbtIvy") {
        override val globsForDefinitionFiles = listOf("build.sbt", "build.scala")

        override fun create(
            analyzerRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) =
            SbtIvy(managerName, analyzerRoot, analyzerConfig, repoConfig)
    }

    private data class SbtProject(val projectName: String, val definitionFile: File, val ivyReportFile: File)

    // See https://github.com/apache/ant-ivy/blob/master/src/java/org/apache/ivy/plugins/report/XmlReportWriter.java
    // and https://github.com/jenkinsci/ivy-report-plugin/blob/master/src/main/resources/jenkins/plugins/ivyreport/ivy-report.xsl.
    private data class IvyReport(
        @JacksonXmlProperty(isAttribute = true)
        val version: String,
        val info: IvyInfo,
        val dependencies: List<IvyModule>?
    ) {
        companion object {
            const val SUPPORTED_VERSION = "1.0"
        }

        init {
            require(version == SUPPORTED_VERSION) {
                "Only Ivy report version $SUPPORTED_VERSION is supported."
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class IvyInfo(
        @JacksonXmlProperty(isAttribute = true)
        val conf: String
    )

    private data class IvyModule(
        @JacksonXmlProperty(isAttribute = true, localName = "organisation")
        val organization: String,
        @JacksonXmlProperty(isAttribute = true)
        val name: String,
        @JsonProperty(value = "revision")
        @JacksonXmlElementWrapper(useWrapping = false)
        val revisions: List<IvyRevision>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class IvyRevision(
        @JacksonXmlProperty(isAttribute = true, localName = "name")
        val version: String,
        @JacksonXmlProperty(isAttribute = true)
        val conf: String,
        @JsonProperty(value = "evicted-by")
        val evictedBy: IvyEviction?,
        val license: IvyLicense?, // Evicted revisions do not have their license set.
        @JsonProperty(value = "metadata-artifact")
        val artifactMetadata: IvyArtifactMetadata?,
        val artifacts: List<IvyArtifact>?
    )

    private data class IvyEviction(
        @JacksonXmlProperty(isAttribute = true, localName = "rev")
        val version: String
    )

    private data class IvyLicense(
        @JacksonXmlProperty(isAttribute = true)
        val name: String,
        @JacksonXmlProperty(isAttribute = true)
        val url: String
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class IvyArtifactMetadata(
        val status: String,
        val location: String?, // This is null if status == "failed".
        @JsonProperty(value = "origin-location")
        val origin: String? // This is null if status == "failed".
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class IvyArtifact(
        @JacksonXmlProperty(isAttribute = true)
        val name: String,
        @JacksonXmlProperty(isAttribute = true)
        val type: String,
        @JacksonXmlProperty(isAttribute = true)
        val ext: String,
        @JacksonXmlProperty(isAttribute = true)
        val location: String,
        @JsonProperty(value = "origin-location")
        val origin: IvyOrigin
    )

    private data class IvyOrigin(
        @JacksonXmlProperty(isAttribute = true, localName = "is-local")
        val isLocal: Boolean,
        @JacksonXmlProperty(isAttribute = true)
        val location: String
    )

    /**
     * A workspace reader that is backed by the local Ivy artifact cache.
     */
    private class IvyCacheReader : WorkspaceReader {
        private val workspaceRepository = WorkspaceRepository("ivyCache")
        private val ivyCacheRoot = getUserHomeDirectory().resolve(".ivy2/cache")

        override fun findArtifact(artifact: Artifact): File? {
            val artifactRootDir = File(
                ivyCacheRoot,
                "${artifact.groupId}/${artifact.artifactId}"
            )

            val relativePath = when (artifact.extension) {
                "pom" -> "ivy-${artifact.version}.xml.original"
                "jar" -> {
                    val classifier = if (artifact.classifier.isNullOrBlank()) "" else "-${artifact.classifier}"
                    "jars/${artifact.artifactId}-${artifact.version}$classifier.${artifact.extension}"
                }
                else -> {
                    log.warn { "Unhandled extension '${artifact.extension}' for artifact $artifact." }
                    null
                }
            }

            val artifactFile = relativePath?.let { artifactRootDir.resolve(it).takeIf { it.isFile } }

            if (artifactFile == null) {
                log.debug { "Artifact $artifact not found at '$artifactFile'." }
            }

            return artifactFile
        }

        override fun findVersions(artifact: Artifact) =
            // Do not resolve versions of already locally available artifacts. This also ensures version resolution
            // was done by Ivy.
            if (findArtifact(artifact)?.isFile == true) listOf(artifact.version) else emptyList()

        override fun getRepository() = workspaceRepository
    }

    private var pluginDeclarationFiles = emptyList<File>()

    override fun command(workingDir: File?) = if (Os.isWindows) "sbt.bat" else "sbt"

    override fun beforeResolution(definitionFiles: List<File>) {
        log.info { "Adding $DEPENDENCY_GRAPH_PLUGIN_NAME plugin version $DEPENDENCY_GRAPH_PLUGIN_VERSION..." }

        val home = getUserHomeDirectory()

        pluginDeclarationFiles = MAJOR_VERSIONS.map {
            val pluginDir = home.resolve(".sbt/$it/plugins")
            createTempFile("ort", ".sbt", pluginDir).apply {
                writeText(DEPENDENCY_GRAPH_PLUGIN_DECLARATION)

                // Delete the file again even if the JVM is killed, e.g. by aborting a debug session.
                deleteOnExit()
            }
        }
    }

    override fun resolveDependencies(definitionFiles: List<File>): ResolutionResult {
        beforeResolution(definitionFiles)

        val workingDir = if (definitionFiles.size > 1) {
            // Some sbt projects do not have a build file in their root, but they still require "sbt" to be run from the
            // project's root directory. In order to determine the root directory, use the common prefix of all
            // definition file paths.
            getCommonFileParent(definitionFiles).also {
                log.info { "Determined '$it' as the $managerName project root directory." }
            }
        } else {
            definitionFiles.first().parentFile
        }

        fun runSbt(vararg command: String) =
            suppressInput {
                run(workingDir, BATCH_MODE, LOG_NO_FORMAT, *command)
            }

        // Get the list of project names. The current project, which by default is the root project, is indicated by an
        // asterisk.
        val internalProjects = runSbt("projects").stdout.lines().mapNotNull { line ->
            PROJECT_REGEX.matchEntire(line)?.groupValues?.let {
                it[2] to (it[1] == "*")
            }
        }

        if (internalProjects.isEmpty()) {
            log.warn { "No sbt projects found inside the '${workingDir.absolutePath}' directory." }
        }

        // In contrast to sbt's built-in "update" command, this plugin-provided command creates Ivy reports per
        // sbt project.
        runSbt("ivyReport")

        val sbtProjects = mutableListOf<SbtProject>()

        // Find Ivy reports for root projects.
        definitionFiles.mapTo(sbtProjects) { definitionFile ->
            val ivyReportFile = definitionFile.resolveSibling("target").walkTopDown().single {
                it.name == IVY_REPORT_FILE_NAME
            }

            SbtProject(definitionFile.parentFile.name, definitionFile, ivyReportFile)
        }

        // Find Ivy reports for programmatically created projects.
        internalProjects.mapNotNullTo(sbtProjects) { (name, isCurrent) ->
            if (isCurrent) {
                null
            } else {
                val ivyReportFile = workingDir.resolve(name).walkTopDown().single {
                    it.name == IVY_REPORT_FILE_NAME
                }

                val definitionFile = definitionFiles.single { it.parentFile == workingDir }
                SbtProject(name, definitionFile, ivyReportFile)
            }
        }

        val result = mutableMapOf<File, ProjectAnalyzerResult>()

        sbtProjects.forEach {
            result[it.definitionFile] = parseSbtProject(it)
        }

        afterResolution(definitionFiles)

        return result
    }

    override fun resolveDependencies(definitionFile: File) =
        // This is not implemented in favor over overriding [resolveDependencies].
        throw NotImplementedError()

    override fun afterResolution(definitionFiles: List<File>) {
        log.info { "Removing $DEPENDENCY_GRAPH_PLUGIN_NAME plugin version $DEPENDENCY_GRAPH_PLUGIN_VERSION..." }

        pluginDeclarationFiles.forEach {
            // Delete the file as early as possible even before the JVM exits.
            it.delete()
        }
    }

    private fun removeEmptyTags(xml: String) =
        xml.replace(Regex("^\\s+<([a-z-]+)>$\\s+</\\1>$", RegexOption.MULTILINE), "")

    private fun normalizeMavenRepoUrl(url: String) =
        url.replace(Regex("^(https?)://repo1\\.maven\\.org/maven2/(.+)$")) {
            "${it.groupValues[1]}://repo.maven.apache.org/maven2/${it.groupValues[2]}"
        }

    private fun parseSbtProject(sbtProject: SbtProject): ProjectAnalyzerResult {
        val packages = sortedSetOf<Package>()
        val scopes = sortedSetOf<Scope>()
        val project = Project(
            id = Identifier(
                type = managerName,
                namespace = "namespace",
                name = sbtProject.projectName,
                version = "version"
            ),
            definitionFilePath = "VersionControlSystem.getPathInfo(packageJson).path",
            declaredLicenses = sortedSetOf("declaredLicenses"),
            vcs = VcsInfo.EMPTY,
            homepageUrl = "homepageUrl",
            scopes = scopes
        )

        val mapper = XmlMapper().registerKotlinModule()

        val scopeFiles = sbtProject.ivyReportFile.parentFile.listFiles { _, name ->
            name.endsWith(".xml") && !name.endsWith("-internal.xml")
        }

        scopeFiles.forEach { scopeFile ->
            // Work around https://github.com/FasterXML/jackson-dataformat-xml/issues/124.
            val scopeXml = removeEmptyTags(scopeFile.readText())

            val ivyReport = mapper.readValue<IvyReport>(scopeXml)

            if (ivyReport.dependencies == null) return@forEach

            val scope = Scope(ivyReport.info.conf)

            ivyReport.dependencies.forEach { module ->
                // There must only be a single non-evicted revision.
                val revision = module.revisions.single { it.evictedBy == null }

                val binaryArtifact = RemoteArtifact(
                    url = normalizeMavenRepoUrl(
                        revision.artifacts?.find { it.type == "jar" }?.origin?.location.orEmpty()
                    ),
                    hash = "",
                    hashAlgorithm = HashAlgorithm.UNKNOWN
                )

                val pkg = if (revision.artifactMetadata != null && revision.artifactMetadata.status != "failed") {
                    val id = Identifier("Maven", module.organization, module.name, revision.version)

                    val pomFile = File("${revision.artifactMetadata.location}.original")
                    require(pomFile.isFile) {
                        "POM file for ${id.toCoordinates()} does not exist at '$pomFile'."
                    }

                    val maven = MavenSupport(IvyCacheReader()).buildMavenProject(pomFile, false)

                    Package(
                        id = id,
                        declaredLicenses = MavenSupport.parseLicenses(maven.project),
                        description = maven.project.description.orEmpty(),
                        homepageUrl = maven.project.url.orEmpty(),
                        binaryArtifact = binaryArtifact,
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = MavenSupport.parseVcsInfo(maven.project)
                    )
                } else {
                    val id = Identifier(managerName, module.organization, module.name, revision.version)

                    Package(
                        id = id,
                        declaredLicenses = sortedSetOf("license"),
                        description = "description",
                        homepageUrl = "homepageUrl",
                        binaryArtifact = binaryArtifact,
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                    )
                }

                packages += pkg
                scope.dependencies += pkg.toReference()
            }

            scopes += scope
        }

        return ProjectAnalyzerResult(project, packages.map { it.toCuratedPackage() }.toSortedSet())
    }
}
