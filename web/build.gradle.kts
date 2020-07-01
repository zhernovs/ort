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

val exposedVersion: String by project
val log4jCoreVersion: String by project
val kotlinReactVersion: String by project
val kotlinReactRouterVersion: String by project
val kotlinStyledVersion: String by project
val kotlinxSerializationVersion: String by project
val ktorSpaVersion: String by project
val ktorVersion: String by project
val npmAbortControllerVersion: String by project
val npmBufferutilVersion: String by project
val npmFsVersion: String by project
val npmReactVersion: String by project
val npmReactRouterVersion: String by project
val npmInlineStylePrefixerVersion: String by project
val npmStyledComponentsVersion: String by project
val npmTextEncodingVersion: String by project
val npmUtf8ValidateVersion: String by project
val postgresVersion: String by project

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    application
}

application {
    applicationName = "web"
    mainClassName = "org.ossreviewtoolkit.web.jvm.ApplicationKt"
}

repositories {
    jcenter()
    maven { url = uri("https://kotlin.bintray.com/kotlin-js-wrappers") }
    maven { url = uri("https://repo.gradle.org/gradle/libs-releases-local/") }
}

kotlin {
    jvm {
        val main by compilations.getting {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }

        // Required for the Gradle application plugin, see:
        // https://youtrack.jetbrains.com/issue/KT-27273#focus=streamItem-27-3487675.0-0
        withJava()
    }

    js {
        browser {
            // Required due to an issue with Ktor and Kotlin 1.3.72:
            // https://kotlinlang.org/docs/reference/javascript-dce.html#known-issue-dce-and-ktor
            dceTask {
                keep("ktor-ktor-io.\$\$importsForInline\$\$.ktor-ktor-io.io.ktor.utils.io")
            }

            distribution {
                directory = file("$buildDir/js")
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$kotlinxSerializationVersion")
            }
        }

        js().compilations["main"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib-js"))

                implementation("io.ktor:ktor-client-js:$ktorVersion")
                implementation("io.ktor:ktor-client-serialization-js:$ktorVersion")
                implementation("org.jetbrains:kotlin-styled:$kotlinStyledVersion")
                implementation("org.jetbrains:kotlin-react:$kotlinReactVersion")
                implementation("org.jetbrains:kotlin-react-dom:$kotlinReactVersion")
                implementation("org.jetbrains:kotlin-react-router-dom:$kotlinReactRouterVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:$kotlinxSerializationVersion")

                implementation(npm("fs", npmFsVersion))
                implementation(npm("inline-style-prefixer", npmInlineStylePrefixerVersion))
                implementation(npm("react", npmReactVersion))
                implementation(npm("react-dom", npmReactVersion))
                implementation(npm("react-router-dom", npmReactRouterVersion))
                implementation(npm("styled-components", npmStyledComponentsVersion))

                // Required by ktor-client-serialization-js.
                implementation(npm("abort-controller", npmAbortControllerVersion))
                implementation(npm("bufferutil", npmBufferutilVersion))
                implementation(npm("text-encoding", npmTextEncodingVersion))
                implementation(npm("utf-8-validate", npmUtf8ValidateVersion))
            }
        }

        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))

                implementation(project(":analyzer"))
                implementation(project(":downloader"))
                implementation(project(":model"))
                implementation(project(":scanner"))

                implementation("com.github.lamba92:ktor-spa:$ktorSpaVersion")
                implementation("io.ktor:ktor-serialization:$ktorVersion")
                implementation("io.ktor:ktor-server-netty:$ktorVersion")
                implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jCoreVersion")
                implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$kotlinxSerializationVersion")
                implementation("org.postgresql:postgresql:$postgresVersion")
            }

            // Add the react app to the classpath.
            resources.srcDir("$buildDir/js")
        }
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                allWarningsAsErrors = true
            }
        }
    }
}

distributions {
    main {
        contents {
            // Add the JAR file created by "jvmJar" to the lib folder, by default only the output of "jar" is added.
            into("lib") {
                from(tasks.named("jvmJar"))
            }
        }
    }
}

tasks.named<CreateStartScripts>("startScripts") {
    // Add the JAR file created by "jvmJar" to the classpath, by default only the output of "jar" is added.
    val files = files(tasks.named<Jar>("jvmJar").get().archiveFile.get().asFile)
    classpath = (classpath ?: files()) + files
}

tasks.named("jvmProcessResources") {
    // Make sure the react app is packed when processing resources for the JVM target.
    dependsOn("jsBrowserWebpack")
}
