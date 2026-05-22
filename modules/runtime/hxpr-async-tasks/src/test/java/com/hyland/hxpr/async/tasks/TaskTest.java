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
 *     Gildas Lefevre
 */
package com.hyland.hxpr.async.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyland.hxpr.async.tasks.message.BatchTask;
import com.hyland.hxpr.async.tasks.message.DocumentTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.transaction.TransactionHelper;

import java.time.Instant;

@ExtendWith(FeaturesExtension.class)
@Features(AsyncTasksFeature.class)
public class TaskTest {

    @Test
    public void propertiesAutomaticallyFilledAtCreation() {
        var task = new DocumentTask("TestTask", "default", "Administrator", "1234");
        assertThat(task.getTimestamp()).isEqualTo(TransactionHelper.getTransactionStartTime().toEpochMilli());

        // When the transaction is not started, the timestamp should be null
        TransactionHelper.commitOrRollbackTransaction();
        task = new DocumentTask("TestTask2", "default", "Administrator", "1234");
        assertThat(task.getTimestamp()).isNull();
    }

    @Test
    public void documentBatchTaskBuilderSetsPropertiesCorrectly() {
        var timestamp = Instant.now().toEpochMilli();
        var batchTask = new BatchTask.Builder("bulkTest", "test", "user", 50)
                .withTaskId("task1234")
                .withNodeId("1234")
                .withTimestamp(timestamp)
                .build();
        assertThat(batchTask.getTaskId()).isEqualTo("task1234");
        assertThat(batchTask.getTaskName()).isEqualTo("bulkTest");
        assertThat(batchTask.getRepository()).isEqualTo("test");
        assertThat(batchTask.getUsername()).isEqualTo("user");
        assertThat(batchTask.getTimestamp()).isEqualTo(timestamp);
        assertThat(batchTask.getNodeId()).isEqualTo("1234");
        assertThat(batchTask.getBatchSize()).isEqualTo(50);
    }

    @Test
    public void jsonCanBeDeserializedAsTask() throws Exception {
        var json = """
                {
                    "taskName":"bucketIndexing",
                    "repository":"default",
                    "username":"system",
                    "retryCount":0,
                    "nodeId":"123",
                    "timestamp":1745952543015,
                    "batchSize":25,
                    "batchTransactionTimeout":10000,
                    "documentIds": ["123", "456"],
                    "taskId":"bucketIndexing:35dcdd9f-270d-4882-80a8-e512c9833e3f"
                }
                """;
        var task = TaskServiceImpl.MAPPER.readValue(json, BatchTask.class);
        assertThat(task.getTaskName()).isEqualTo("bucketIndexing");
        assertThat(task.getRepository()).isEqualTo("default");
        assertThat(task.getUsername()).isEqualTo("system");
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getNodeId()).isEqualTo("123");
        assertThat(task.getTimestamp()).isEqualTo(1745952543015L);
        assertThat(task.getBatchSize()).isEqualTo(25);
        assertThat(task.getBatchTransactionTimeout()).isEqualTo(10000L);
        assertThat(task.getDocumentIds()).containsExactly("123", "456");
        assertThat(task.getTaskId()).isEqualTo("bucketIndexing:35dcdd9f-270d-4882-80a8-e512c9833e3f");
    }

    @Test
    public void jsonCanBeDeserializedAsTaskStatus() throws Exception {
        var json = """
                {
                    "username":"system",
                    "state":"RUNNING",
                    "submitTime":"2026-02-23T08:28:22.496Z",
                    "task":"indexing",
                    "errorCount":0,
                    "errorMessage":null,
                    "errorCode":0,
                    "batchCount":1,
                    "processed":10,
                    "id":"indexing:971d8041-a649-49b7-9d5c-2d6e83b59149"
                }
                """;
        var task = TaskServiceImpl.MAPPER.readValue(json, TaskStatus.class);
        assertThat(task.getUsername()).isEqualTo("system");
        assertThat(task.getState()).isEqualTo(TaskStatus.State.RUNNING);
        assertThat(task.getSubmitTime()).isEqualTo("2026-02-23T08:28:22.496Z");
        assertThat(task.getTask()).isEqualTo("indexing");
        assertThat(task.getErrorCount()).isEqualTo(0);
        assertThat(task.getErrorMessage()).isNull();
        assertThat(task.getErrorCode()).isEqualTo(0);
        assertThat(task.getProcessed()).isEqualTo(10);
        assertThat(task.getId()).isEqualTo("indexing:971d8041-a649-49b7-9d5c-2d6e83b59149");
    }

    @Test
    public void jsonCanBeDeserializedAsTaskStatusWithNullSubmitTime() throws Exception {
        var json = """
                {
                    "username":"system",
                    "state":"RUNNING",
                    "submitTime":null,
                    "task":"indexing",
                    "errorCount":0,
                    "errorMessage":null,
                    "errorCode":0,
                    "batchCount":1,
                    "processed":10,
                    "id":"indexing:971d8041-a649-49b7-9d5c-2d6e83b59149"
                }
                """;
        var task = TaskServiceImpl.MAPPER.readValue(json, TaskStatus.class);
        assertThat(task.getUsername()).isEqualTo("system");
        assertThat(task.getState()).isEqualTo(TaskStatus.State.RUNNING);
        assertThat(task.getSubmitTime()).isNull();
        assertThat(task.getTask()).isEqualTo("indexing");
        assertThat(task.getErrorCount()).isEqualTo(0);
        assertThat(task.getErrorMessage()).isNull();
        assertThat(task.getErrorCode()).isEqualTo(0);
        assertThat(task.getProcessed()).isEqualTo(10);
        assertThat(task.getId()).isEqualTo("indexing:971d8041-a649-49b7-9d5c-2d6e83b59149");
    }

}
