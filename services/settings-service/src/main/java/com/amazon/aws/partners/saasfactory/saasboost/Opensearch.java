/**
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
package com.amazon.aws.partners.saasfactory.saasboost;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = Opensearch.Builder.class)
public class Opensearch {
    
    private final String engineVersion;
    private final String dataInstanceType;
    private final String cognitoUserPool;
    private final String cognitoIdentityPool;


    private Opensearch(Builder builder) {
        this.engineVersion = builder.engineVersion;
        this.dataInstanceType = builder.dataInstanceType;
        this.cognitoUserPool = builder.cognitoUserPool;
        this.cognitoIdentityPool = builder.cognitoIdentityPool;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public String getDataInstanceType() {
        return dataInstanceType;
    }

    public String getCognitoUserPool() {
        return cognitoUserPool;
    }

    public String getCognitoIdentityPool() {
        return cognitoIdentityPool;
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        private String engineVersion;
        private String dataInstanceType;
        private String cognitoUserPool;
        private String cognitoIdentityPool;

        private Builder() {}

        public Builder engineVersion(String engineVersion) {
            this.engineVersion = engineVersion;
            return this;
        }

        public Builder dataInstanceType(String dataInstanceType) {
            this.dataInstanceType = dataInstanceType;
            return this;
        }

        public Builder cognitoUserPool(String cognitoUserPool) {
            this.cognitoUserPool = cognitoUserPool;
            return this;
        }

        public Builder cognitoIdentityPool(String cognitoIdentityPool) {
            this.cognitoIdentityPool = cognitoIdentityPool;
            return this;
        }

        public Opensearch build(){
            return new Opensearch(this);
        }

    }
    
}
