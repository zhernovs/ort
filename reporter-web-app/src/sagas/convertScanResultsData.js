/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
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

import { delay, put } from 'redux-saga/effects';

function* convertScanResultsData(packageScanResultsData) {
    const convertedData = [];

    console.log('convertScanResultsData', packageScanResultsData);
    for (let i = packageScanResultsData.length - 1; i >= 0; i -= 1) {
        const { scanner, summary } = packageScanResultsData[i];
        const { license_findings: findings } = summary;

        for (let j = findings.length - 1; j >= 0; j -= 1) {
            const { license, locations: licenseLocations, copyrights } = findings[j];

            for (let x = licenseLocations.length - 1; x >= 0; x -= 1) {
                convertedData.unshift({
                    license,
                    ...licenseLocations[x],
                    scanner: { ...scanner },
                    type: 'license'
                });

                yield delay(10);
            }

            for (let y = copyrights.length - 1; y >= 0; y -= 1) {
                const { locations: copyrightLocations, statement } = copyrights[y];
                for (let z = copyrightLocations.length - 1; z >= 0; z -= 1) {
                    convertedData.push({
                        license,
                        ...copyrightLocations[z],
                        scanner: { ...scanner },
                        statement,
                        type: 'copyright'
                    });
                }

                yield delay(10);
            }
        }

        yield delay(10);
    }

    yield put({ type: 'PKG::CONVERTING_SCAN_RESULTS_DONE', payload: { ...convertedData } });
    yield delay(50);

    console.log('convertedData', convertedData);

    return convertedData;
}

export default convertScanResultsData;
