package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

sealed class Provider(val name: String)

class ProjectProvider(name: Name) : Provider(name.value) {
    enum class Name(val value: String) {
        MAVEN("Maven");

        companion object {
            @JsonCreator
            @JvmStatic
            fun fromString(value: String) =
                enumValues<Name>().single { value.equals(it.value, ignoreCase = true) }
        }

        @JsonValue
        override fun toString() = value
    }
}

class PackageProvider() {
    enum class Name {
        MAVEN("Maven")
    }
}
