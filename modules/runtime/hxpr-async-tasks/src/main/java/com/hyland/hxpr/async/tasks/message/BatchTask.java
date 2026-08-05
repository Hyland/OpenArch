/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.hxpr.async.tasks.message;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;
import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Asynchronous bulk task description defining all required information for executing a bulk task.
 */
public class BatchTask extends AbstractTask {

    @JsonInclude(Include.NON_NULL)
    protected List<String> documentIds;

    protected Long batchTransactionTimeout;

    protected int batchSize;

    public BatchTask() {
        // Empty constructor for Jackson deserialization
    }

    private BatchTask(String taskId, String taskName, String repository, String username, String nodeId, Long timestamp, int batchSize, Long batchTransactionTimeout, List<String> documentIds, Map<String, Serializable> properties) {
        super(taskId, taskName, repository, username, nodeId, timestamp);
        if (isEmpty(username)) {
            throw new IllegalArgumentException("Bulk task username cannot be empty");
        }
        this.batchSize = batchSize;
        this.batchTransactionTimeout = batchTransactionTimeout;
        this.documentIds = new ArrayList<>(documentIds);
        this.properties = new HashMap<>(properties);
    }

    public int getBatchSize() {
        return batchSize;
    }

    @JsonIgnore
    public Duration getBatchTransactionTimeoutDuration() {
        return Duration.ofSeconds(requireNonNullElse(batchTransactionTimeout, 0L));
    }

    public Long getBatchTransactionTimeout() {
        return batchTransactionTimeout;
    }

    public List<String> getDocumentIds() {
        return documentIds;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BatchTask other = (BatchTask) obj;
        if (!StringUtils.equals(taskName, other.taskName)) {
            return false;
        }
        if (!StringUtils.equals(repository, other.repository)) {
            return false;
        }
        if (!StringUtils.equals(username, other.username)) {
            return false;
        }
        return documentIds.equals(other.documentIds);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        var result = 1;
        result = prime * result + ((taskName == null) ? 0 : taskName.hashCode());
        result = prime * result + ((repository == null) ? 0 : repository.hashCode());
        result = prime * result + ((username == null) ? 0 : username.hashCode());
        result = prime * result + ((documentIds == null) ? 0 : documentIds.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "AWSBulkActionRecord{" + "taskName=" + taskName + ", repository=" + repository + ", username=" + username
                + ", documentIds=" + documentIds + "}";
    }

    public static class Builder {

        String taskId;

        String taskName;

        String repository;

        String username;

        String nodeId;

        Long timestamp;

        List<String> documentIds;

        int batchSize;

        Long batchTransactionTimeout;

        Map<String, Serializable> properties;

        public Builder(String taskName, String repository, String username, int batchSize) {
            this.taskName = taskName;
            this.repository = repository;
            this.username = username;
            this.batchSize = batchSize;
            this.documentIds = new ArrayList<>();
            this.properties = new HashMap<>();
        }

        public Builder withTaskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder withNodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder withBatchTransactionTimeout(Long timeout) {
            this.batchTransactionTimeout = timeout;
            return this;
        }

        public Builder withTimestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder withDocumentIds(List<String> documentIds) {
            this.documentIds.addAll(documentIds);
            return this;
        }

        public Builder withProperties(Map<String, Serializable> properties) {
            if (properties != null && !properties.isEmpty()) {
                if (properties.containsKey(null)) {
                    throw new IllegalArgumentException("Param key cannot be null");
                }
                this.properties = properties;
            }
            return this;
        }

        public BatchTask build() {
            return new BatchTask(taskId, taskName, repository, username, nodeId, timestamp, batchSize, batchTransactionTimeout, documentIds, properties);
        }
    }
}
