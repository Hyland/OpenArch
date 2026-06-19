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

import java.util.List;

public record ReportBulkImport(BulkImport job, List<BulkImportFailureDTO> documentsFailed) {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(BulkImport job, List<BulkImportFailureDTO> documentsFailed) {
        return new Builder(job, documentsFailed);
    }

    public static class Builder {
        private BulkImport job;

        private List<BulkImportFailureDTO> documentsFailed;

        public Builder() {
        }

        public Builder(BulkImport job, List<BulkImportFailureDTO> documentsFailed) {
            this.job = job;
            this.documentsFailed = documentsFailed;
        }

        public Builder bulkImport(BulkImport job) {
            this.job = job;
            return this;
        }

        public Builder bulkImportFailureDTO(List<BulkImportFailureDTO> documentsFailed) {
            this.documentsFailed = documentsFailed;
            return this;
        }

        public ReportBulkImport build() {
            return new ReportBulkImport(job, documentsFailed);
        }
    }
}
