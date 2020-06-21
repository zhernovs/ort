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

package org.ossreviewtoolkit.web.jvm.dao

import com.fasterxml.jackson.databind.ObjectMapper

import kotlin.reflect.KClass

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

import org.postgresql.util.PGobject

fun <T : Any> Table.jsonb(name: String, klass: KClass<T>, jsonMapper: ObjectMapper): Column<T> =
    registerColumn(name, JsonB(klass, jsonMapper))

private class JsonB<T : Any>(private val klass: KClass<T>, private val jsonMapper: ObjectMapper) : ColumnType() {
    override fun sqlType() = "JSONB"

    override fun notNullValueToDB(value: Any): Any = jsonMapper.writeValueAsString(value).escapeNull()

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        stmt[index] = PGobject().apply {
            type = sqlType()
            this.value = value as String
        }
    }

    override fun valueFromDB(value: Any): Any =
        when (value) {
            is PGobject -> jsonMapper.readValue(value.value.unescapeNull(), klass.java)
            else -> value
        }

    /**
     * The null character "\u0000" can appear in raw scan results, for example in ScanCode if the matched text for a
     * license or copyright contains this character. Since it is not allowed in PostgreSQL JSONB columns we need to
     * escape it before writing a string to the database.
     * See: [https://www.postgresql.org/docs/11/datatype-json.html]
     */
    private fun String.escapeNull() = replace("\\u0000", "\\\\u0000")

    /**
     * Unescape the null character "\u0000". For details see [escapeNull].
     */
    private fun String.unescapeNull() = replace("\\\\u0000", "\\u0000")
}
