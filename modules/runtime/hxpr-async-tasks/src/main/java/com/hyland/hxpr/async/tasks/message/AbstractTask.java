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

import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.nuxeo.runtime.transaction.TransactionHelper;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractTask implements Task {

    private static final String TASK_ID = "taskId";

    private static final int DEFAULT_RETRY_COUNT = 0;

    @JsonProperty(TASK_ID)
    protected String id;

    // the taskName attribute is used by SNS/SQS subscription FilterPolicy
    protected String taskName;

    protected String repository;

    protected String username;

    protected int retryCount = DEFAULT_RETRY_COUNT;

    protected String nodeId;

    protected Long timestamp;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected Map<String, Serializable> properties = new HashMap<>();

    public AbstractTask() {
        // Empty constructor for Jackson deserialization
    }

    protected AbstractTask(String taskId, String taskName, String repository, String username, String nodeId,
            Long timestamp) {
        if (isEmpty(taskName)) {
            throw new IllegalArgumentException("Task name cannot be empty");
        }
        if (taskId == null) {
            taskId = taskName + ":" + UUID.randomUUID();
        }
        this.id = taskId;
        this.taskName = taskName;
        this.repository = repository;
        this.username = username;
        this.nodeId = nodeId;
        if (timestamp == null) {
            var transactionInstant = TransactionHelper.getTransactionStartTime();
            if (transactionInstant != null) {
                timestamp = transactionInstant.toEpochMilli();
            }
        }
        this.timestamp = timestamp;
    }

    protected AbstractTask(String taskName, String repository, String username) {
        this(null, taskName, repository, username, null, null);
    }

    @Override
    public String getTaskId() {
        return id;
    }

    @Override
    public String getTaskName() {
        return taskName;
    }

    @Override
    public String getRepository() {
        return repository;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public int getRetryCount() {
        return retryCount;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public Long getTimestamp() {
        return timestamp;
    }

    @Override
    public Map<String, Serializable> getProperties() {
        return properties;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}
