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

import React from 'react';
import { Tabs, Table } from 'antd';
import PropTypes from 'prop-types';
import ExpandablePanel from './ExpandablePanel';
import ExpandablePanelContent from './ExpandablePanelContent';
import ExpandablePanelTitle from './ExpandablePanelTitle';

const { TabPane } = Tabs;

// Generates the HTML to display the scan results for a package
const PackageScanResults = (props) => {
    const { data, show } = props;
    const pkgObj = data;
    console.log('show', show);
    const show2 = true;

    // Do not render anything if no scan results
    if (Array.isArray(pkgObj.results) && pkgObj.results.length === 0) {
        return null;
    }

    console.log('pkg', pkgObj);

    return (
        <ExpandablePanel key="ort-package-scan-results" show={show2}>
            <ExpandablePanelTitle titleElem="h4">Package Scan Results</ExpandablePanelTitle>
            <ExpandablePanelContent>
                <Tabs tabPosition="top">
                    <TabPane
                        key="ort-scan-results-summary"
                        tab="Summary"
                    >
                        <Table
                            columns={[
                                {
                                    dataIndex: 'license',
                                    filters: (() => pkgObj.detected_licenses
                                        .reduce((accumulator, license) => {
                                            accumulator.push({
                                                text: license,
                                                value: license
                                            });

                                            return accumulator;
                                        }, [])
                                    )(),
                                    onFilter: (value, record) => record.license.includes(value),
                                    title: 'License',
                                    render: (text, row) => (
                                        <div>
                                            <dl>
                                                <dt>
                                                    {row.license}
                                                </dt>
                                            </dl>
                                            <dl>
                                                {row.copyrights.map(holder => (
                                                    <dd key={`${row.license}-holder-${holder.statement}`}>
                                                        {holder.statement}
                                                    </dd>
                                                ))}
                                            </dl>
                                        </div>
                                    )
                                },
                                {
                                    title: 'Scanner',
                                    dataIndex: 'scanner',
                                    filters: (() => pkgObj.scanners
                                        .reduce((accumulator, scanner) => {
                                            accumulator.push({
                                                text: scanner,
                                                value: scanner
                                            });

                                            return accumulator;
                                        }, [])
                                    )(),
                                    onFilter:
                                        (value, record) => value === `${record.scanner.name} ${record.scanner.version}`,
                                    render: (text, row) => (
                                        <div>
                                            {`${row.scanner.name} ${row.scanner.version}`}
                                        </div>
                                    )
                                }
                            ]}
                            dataSource={pkgObj.scan_results}
                            locale={{
                                emptyText: 'No scan results'
                            }}
                            pagination={
                                {
                                    defaultPageSize: 25,
                                    hideOnSinglePage: true,
                                    pageSizeOptions: ['50', '100', '250', '500'],
                                    position: 'bottom',
                                    showQuickJumper: true,
                                    showSizeChanger: true,
                                    showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} errors`
                                }
                            }
                            rowKey="license"
                            size="small"
                        />
                    </TabPane>
                    <TabPane
                        key="ort-scan-results-raw"
                        tab="Raw Scan Results"
                    >
                        <span>temp</span>
                    </TabPane>
                </Tabs>
            </ExpandablePanelContent>
        </ExpandablePanel>
    );
};

PackageScanResults.propTypes = {
    data: PropTypes.object.isRequired,
    show: PropTypes.bool
};

PackageScanResults.defaultProps = {
    show: false
};

export default PackageScanResults;
