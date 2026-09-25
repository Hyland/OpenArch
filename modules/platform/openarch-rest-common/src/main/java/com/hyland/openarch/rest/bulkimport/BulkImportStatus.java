/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
 */
package com.hyland.openarch.rest.bulkimport;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@Schema(name = "Status", title = "Status", description = "Represents a Status with Bulk Import Jobs.", accessMode = AccessMode.READ_ONLY)
public record BulkImportStatus(@JsonInclude(JsonInclude.Include.NON_EMPTY) int index, //
        @JsonInclude(JsonInclude.Include.NON_EMPTY) int count, //
        @JsonInclude(JsonInclude.Include.NON_EMPTY) int total, //
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<BulkImport> jobs) {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(BulkImportStatus bulkImportStatus) {
        return new Builder(bulkImportStatus);
    }

    public static class Builder {
        private int index;

        private int count;

        private int total;

        private List<BulkImport> jobs;

        public Builder() {
        }

        public Builder(BulkImportStatus bulkImportStatus) {
            this.index = bulkImportStatus.index;
            this.count = bulkImportStatus.count;
            this.total = bulkImportStatus.total;
            this.jobs = bulkImportStatus.jobs;
        }

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        public Builder count(int count) {
            this.count = count;
            return this;
        }

        public Builder total(int totalRecords) {
            this.total = totalRecords;
            return this;
        }

        public Builder jobs(List<BulkImport> jobs) {
            this.jobs = jobs;
            return this;
        }

        public BulkImportStatus build() {
            return new BulkImportStatus(index, count, total, jobs);
        }
    }
}
