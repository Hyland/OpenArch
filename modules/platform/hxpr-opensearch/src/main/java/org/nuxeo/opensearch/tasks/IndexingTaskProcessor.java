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
package org.nuxeo.opensearch.tasks;

import static com.hyland.openarch.async.tasks.TaskStatus.State.COMPLETED;
import static org.nuxeo.opensearch.OpenSearchConstants.REINDEX_BUCKET_READ_PROPERTY;
import static org.nuxeo.opensearch.OpenSearchServiceImpl.DEFAULT_BUCKET_SIZE;
import static org.nuxeo.opensearch.OpenSearchServiceImpl.PROPERTY_REINDEXING_COMMAND;
import static org.nuxeo.opensearch.tasks.IndexingBatchTaskProcessor.BATCH_INDEXING_TASK_NAME;
import static org.nuxeo.opensearch.tasks.IndexingScrollerTaskProcessor.INDEXING_SCROLLER_TASK_NAME;
import static com.hyland.openarch.runtime.api.login.LoginService.SYSTEM_USERNAME;

import org.nuxeo.opensearch.Timestamp;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.commands.IndexingCommand;
import org.nuxeo.opensearch.tasks.message.IndexingTask;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.openarch.async.tasks.TaskProcessor;
import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.async.tasks.message.ScrollerTask;
import com.hyland.openarch.async.tasks.message.Task;

public class IndexingTaskProcessor implements TaskProcessor<IndexingTask> {

    public static final String INDEXING_TASK_NAME = "indexing";

    private static final Logger log = LoggerFactory.getLogger(IndexingTaskProcessor.class);

    private static final String OPENSEARCH_INDEXING_TASK_TAG = "OpenSearchService indexing task";

    @Override
    public String getName() {
        return INDEXING_TASK_NAME;
    }

    @Override
    public Class<IndexingTask> getTaskClass() {
        return IndexingTask.class;
    }

    @Override
    public void process(IndexingTask task) {
        TransactionHelper.runInTransaction(() -> {
            OpenSearchService oss = Framework.getService(OpenSearchService.class);
            doIndexingWork(oss, task);
        });

        // Update the status of the indexing task
        var asyncTasksService = Framework.getService(TaskService.class);
        var status = asyncTasksService.getStatus(task.getTaskId());
        if (status != null) {
            // The status can be null if the task has been triggered synchronously
            status.addProcessed(task.getCommands().size());
            // The indexing task's own work is done: non-recursive commands are indexed, and any
            // recursive commands have been handed off to an independent scrollerIndexing task.
            // Mark COMPLETED here in all cases; the scroller tracks its own progress separately.
            status.setState(COMPLETED);
            log.debug(OPENSEARCH_INDEXING_TASK_TAG + " {} is completed.", task.getTaskId());
            asyncTasksService.setStatus(status);
        }
    }

    private void doIndexingWork(OpenSearchService oss, IndexingTask task) {
        long now = Timestamp.currentTimeMicros();
        var cmds = task.getCommands();
        for (IndexingCommand cmd : cmds) {
            cmd.setOrder(now);
        }
        oss.indexNonRecursive(cmds, (Boolean) task.getProperties().getOrDefault(PROPERTY_REINDEXING_COMMAND, false));
        TaskService asyncTasksService = Framework.getService(TaskService.class);
        for (IndexingCommand cmd : cmds) {
            if (needRecurse(cmd)) {
                asyncTasksService.submitTask(getTask(cmd, task.getTaskId(), task.getNodeId(), task.getTimestamp()));
            }
        }
    }

    private Task getTask(IndexingCommand cmd, String taskId, String nodeId, Long timestamp) {
        String query;
        if (cmd.getType() == IndexingCommand.Type.UPDATE_DIRECT_CHILDREN) {
            query = "SELECT sys_id FROM SysContent WHERE sys_parentId = '%s'";
        } else {
            query = "SELECT sys_id FROM SysContent WHERE sys_ancestorIds = '%s'";
        }
        query = query.formatted(cmd.getTargetDocumentId());
        // Use a derived task ID so the scroller has its own independent TaskStatus entry.
        // The "-scroller" suffix keeps the "indexing:" prefix, which ensures awaitCompletion's
        // prefix scan still discovers this task and all its descendant batch tasks.
        return new ScrollerTask.Builder(INDEXING_SCROLLER_TASK_NAME, BATCH_INDEXING_TASK_NAME, query,
                cmd.getRepositoryName(), SYSTEM_USERNAME).withTaskId(taskId + "-scroller")
                                                         .withNodeId(nodeId)
                                                         .withTimestamp(timestamp)
                                                         .withBatchSize(getBatchSize())
                                                         .build();
    }

    private boolean needRecurse(IndexingCommand cmd) {
        if (cmd.isRecurse()) {
            return switch (cmd.getType()) {
                case INSERT, UPDATE, UPDATE_SECURITY, UPDATE_DIRECT_CHILDREN -> true;
                case DELETE ->
                    // recurse deletion is done atomically
                        false;
            };
        }
        return false;
    }

    protected int getBatchSize() {
        String value = Framework.getProperty(REINDEX_BUCKET_READ_PROPERTY, DEFAULT_BUCKET_SIZE);
        return Integer.parseInt(value);
    }
}
