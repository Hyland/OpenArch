/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Simple BulkImport representation, useful for json serialization and swagger schema generation with the ReST API.
 */
@Schema(name = "Bulk", title = "Bulk", description = "Represents a Bulk Import Job Descriptor.", accessMode = AccessMode.READ_ONLY)
public record BulkImport(String jobId, //
        String name, //
        String description, //
        String creator, //
        String status, //
        Instant createdAt, //
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant startedAt, //
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant completedAt, //
        String queue, //
        String uniqueKey, //
        long importedDocuments, //
        long totalDocuments, //
        long failedDocuments, //
        String bucket, //
        @JsonInclude(JsonInclude.Include.NON_NULL) boolean dryRun, //
        @JsonInclude(JsonInclude.Include.NON_EMPTY) String awsAccessKeyId, //
        @JsonInclude(JsonInclude.Include.NON_EMPTY) String awsSecretAccessKey, //
        @JsonInclude(JsonInclude.Include.NON_EMPTY) String awsSessionToken) {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(BulkImport job) {
        return new Builder(job);
    }

    public static class Builder {
        private String jobId;

        private String name;

        private String description;

        private String creator;

        private String status;

        private Instant createdAt;

        private Instant startedAt;

        private Instant completedAt;

        private String queue;

        private String uniqueKey;

        private long importedDocuments;

        private long totalDocuments;

        private long failedDocuments;

        private String bucket;

        private boolean dryRun;

        private String awsAccessKeyId;

        private String awsSecretAccessKey;

        private String awsSessionToken;

        public Builder() {
        }

        public Builder(BulkImport job) {
            this.jobId = job.jobId;
            this.name = job.name;
            this.description = job.description;
            this.creator = job.creator;
            this.status = job.status;
            this.createdAt = job.createdAt;
            this.startedAt = job.startedAt;
            this.completedAt = job.completedAt;
            this.queue = job.queue;
            this.uniqueKey = job.uniqueKey;
            this.importedDocuments = job.importedDocuments;
            this.totalDocuments = job.totalDocuments;
            this.failedDocuments = job.failedDocuments;
            this.bucket = job.bucket;
            this.dryRun = job.dryRun;
            this.awsAccessKeyId = job.awsAccessKeyId;
            this.awsSecretAccessKey = job.awsSecretAccessKey;
            this.awsSessionToken = job.awsSessionToken;
        }

        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder creator(String creator) {
            this.creator = creator;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder queue(String queue) {
            this.queue = queue;
            return this;
        }

        public Builder uniqueKey(String uniqueKey) {
            this.uniqueKey = uniqueKey;
            return this;
        }

        public Builder importedDocuments(long importedDocuments) {
            this.importedDocuments = importedDocuments;
            return this;
        }

        public Builder totalDocuments(long totalDocuments) {
            this.totalDocuments = totalDocuments;
            return this;
        }

        public Builder failedDocuments(long failedDocuments) {
            this.failedDocuments = failedDocuments;
            return this;
        }

        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder awsAccessKeyId(String awsAccessKeyId) {
            this.awsAccessKeyId = awsAccessKeyId;
            return this;
        }

        public Builder awsSecretAccessKey(String awsSecretAccessKey) {
            this.awsSecretAccessKey = awsSecretAccessKey;
            return this;
        }

        public Builder awsSessionToken(String awsSessionToken) {
            this.awsSessionToken = awsSessionToken;
            return this;
        }

        public BulkImport build() {
            return new BulkImport(jobId, name, description, creator, status, createdAt, startedAt, completedAt, queue,
                    uniqueKey, importedDocuments, totalDocuments, failedDocuments, bucket, dryRun, awsAccessKeyId,
                    awsSecretAccessKey, awsSessionToken);
        }
    }
}
