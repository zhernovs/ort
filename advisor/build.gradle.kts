import java.net.URL

plugins {
    // Apply core plugins.
    `java-library`

    // Apply third-party plugins.
    id("org.openapi.generator")
}

openApiGenerate {
    val schemaUrl = "https://vulners.com/api/v3/swagger.yaml"
    val schemaText = URL(schemaUrl).readText()
    val schemaFile = file("build/tmp/swagger-vulners.yaml").apply {
        parentFile.mkdirs()
        writeText(schemaText)
    }

    generatorName.set("kotlin")
    inputSpec.set(schemaFile.path)

    // There are errors in the spec, but code generation still works.
    validateSpec.set(false)
}
