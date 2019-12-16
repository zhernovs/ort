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

package com.here.ort.model

/**
 * This class contains information about original and new values when applying a curation.
 */
data class PackageCurationResult(
    /**
     * Contains the original values of the properties which are changed by the [curation]. Values which are not changed
     * are null.
     */
    val base: PackageCurationData,

    /**
     * Contains the new values for of the curated properties.
     */
    val curation: PackageCurationData
)
