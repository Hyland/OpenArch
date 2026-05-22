/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
 *     Gildas Lefevre
 */
package com.hyland.hxpr.async.tasks.message;

import static com.hyland.hxpr.async.tasks.scroller.BatchingScrollerTaskProcessor.BATCHING_SCROLLER_TASK_NAME;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Scroller message sent when trying to trigger a bulk asynchronous taskName, the scroller record will be processed by
 * the configured scroller, resolves the document ids and creates the bulk taskName batches based on the settings.
 */
public class ScrollerTask extends AbstractTask {

    protected String bulkTaskName;

    protected String query;

    protected int batchSize;

    public ScrollerTask() {
        // Empty constructor for Jackson deserialization
    }

    private ScrollerTask(String taskId, String taskName, String bulkTaskName, String repository, String username,
            String nodeId, Long timestamp, String query, int batchSize, Map<String, Serializable> properties) {
        super(taskId, taskName, repository, username, nodeId, timestamp);
        this.bulkTaskName = bulkTaskName;
        this.query = query;
        this.batchSize = batchSize;
        this.properties = new HashMap<>(properties);
    }

    @Override
    public boolean requiresRunningGuard() {
        return true;
    }

    public String getBulkTaskName() {
        return bulkTaskName;
    }

    public String getQuery() {
        return query;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public static class Builder {

        String taskName;

        String bulkTaskName;

        String taskId;

        String query;

        String repository;

        String username;

        String nodeId;

        Long timestamp;

        int batchSize;

        Map<String, Serializable> properties;

        public Builder(String taskName, String bulkTaskName, String query, String repository, String username) {
            Objects.requireNonNull(query, "query cannot be null");
            this.taskName = taskName;
            this.bulkTaskName = bulkTaskName;
            this.query = query;
            this.repository = repository;
            this.username = username;
            this.batchSize = 0;
            this.properties = new HashMap<>();
        }

        public Builder(String bulkTaskName, String query, String repository, String username) {
            this(BATCHING_SCROLLER_TASK_NAME, bulkTaskName, query, repository, username);
        }

        public Builder withTaskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder withBatchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder withProperties(Map<String, Serializable> properties) {
            this.properties.putAll(properties);
            return this;
        }

        public Builder withNodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder withTimestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ScrollerTask build() {
            return new ScrollerTask(taskId, taskName, bulkTaskName, repository, username, nodeId, timestamp, query,
                    batchSize, properties);
        }

        public Builder param(String paramName, Serializable value) {
            this.properties.put(paramName, value);
            return this;
        }
    }
}
