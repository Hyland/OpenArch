/*
 * (C) Copyright 2025 Hyland (https://www.hyland.com/) and others.
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
 * Contributors:
 *     Ananya Banerjee
 */
package org.nuxeo.rest.bulkimport;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BulkImportFailureDTO(BulkImportMetadata metadata, //
        @JsonProperty("error") String error, //
        @JsonProperty("timestamp") Instant timestamp) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BulkImportMetadata metadata;

        private String error;

        private Instant timestamp;

        public Builder metadata(BulkImportMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public BulkImportFailureDTO build() {
            return new BulkImportFailureDTO(metadata, error, timestamp);
        }
    }
}
