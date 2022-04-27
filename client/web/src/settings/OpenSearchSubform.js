/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from "react";
import { Row, Col, Card, CardHeader, CardBody } from "reactstrap";
import {
  SaasBoostInput,
  SaasBoostCheckbox,
} from "../components/FormComponents";

export default function OpenSearchSubform(props) {
  const { provisionOpenSearch = false } = props;
  return (
    <>
      <Row>
        <Col xs={12}>
          <Card>
            <CardHeader>OpenSearch</CardHeader>
            <CardBody>
              <SaasBoostCheckbox
                name="provisionOpenSearch"
                id="provisionOpenSearch"
                label="Configure OpenSearch Domain"
                value={provisionOpenSearch}
              />
              {provisionOpenSearch && (
                <Row>
                  <Col xl={6}>
                    <SaasBoostInput
                      key="opensearch.engineVersion"
                      label="Please enter the opensearch enginer version"
                      name="opensearch.engineVersion"
                      type="text"
                    />
                    <SaasBoostInput
                      key="opensearch.dataInstanceType"
                      label="Please enter the opensearch data instance type"
                      name="opensearch.dataInstanceType"
                      type="text"
                    />
                  </Col>
                </Row>
              )}
            </CardBody>
          </Card>
        </Col>
      </Row>
    </>
  );
}