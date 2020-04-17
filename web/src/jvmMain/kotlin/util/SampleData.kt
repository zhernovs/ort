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

package org.ossreviewtoolkit.web.jvm.util

import org.jetbrains.exposed.sql.Transaction

import org.ossreviewtoolkit.web.jvm.dao.OrtProjectDao

/**
 * Create sample data in the database to make it easier for first time users to understand how the web application
 * works.
 */
internal fun Transaction.createSampleData() {
    if (OrtProjectDao.count() == 0L) {
        OrtProjectDao.new {
            name = "ORT NPM Test Project"
            type = "Git"
            url = "https://github.com/oss-review-toolkit/ort.git"
            path = "analyzer/src/funTest/assets/projects/synthetic/npm"
        }
    }
}
