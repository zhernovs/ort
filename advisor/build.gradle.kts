val ossindexServiceClientVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
}

dependencies {
    implementation("org.sonatype.ossindex:ossindex-service-client:$ossindexServiceClientVersion")
}
