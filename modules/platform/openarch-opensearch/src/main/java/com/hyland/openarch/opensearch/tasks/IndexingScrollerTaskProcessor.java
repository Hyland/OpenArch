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
package com.hyland.openarch.opensearch.tasks;

import static com.hyland.openarch.opensearch.OpenSearchServiceImpl.PROPERTY_REINDEXING_COMMAND;

import java.util.List;

import com.hyland.openarch.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.async.tasks.message.ScrollerTask;
import com.hyland.openarch.async.tasks.scroller.BatchingScrollerTaskProcessor;

/**
 * Processes ScrollerTask tasks for document indexing
 */
public class IndexingScrollerTaskProcessor extends BatchingScrollerTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(IndexingScrollerTaskProcessor.class);

    public static final String INDEXING_SCROLLER_TASK_NAME = "scrollerIndexing";

    public static final String PROPERTY_SYNC_ALIAS = "syncAlias";

    private static final int WARN_DOC_COUNT = 500;

    @Override
    public String getName() {
        return INDEXING_SCROLLER_TASK_NAME;
    }

    @Override
    protected void process(ScrollerTask task, List<String> documentIds, int batchNumber) {
        if (documentIds.isEmpty()) {
            return;
        }
        submitTask(task, documentIds, batchNumber, false);
    }

    @Override
    protected void handlePostProcessing(ScrollerTask task) {
        boolean syncAlias = (Boolean) task.getProperties().getOrDefault(PROPERTY_SYNC_ALIAS, false);
        if (!syncAlias) {
            return;
        }
        // Use batchNumber=0 as the unique post-processing batch identifier ("{scrollerTaskId}-0")
        submitTask(task, List.of(), 0, true);
    }

    @Override
    protected void handleFinalProcessing(ScrollerTask task, int documentCount, int bucketCount) {
        boolean syncAlias = (Boolean) task.getProperties().getOrDefault(PROPERTY_SYNC_ALIAS, false);
        if (syncAlias || documentCount > WARN_DOC_COUNT) {
            String message = String.format("Re-indexing task: %s has submited %d documents in %d bucket tasks",
                    task.getTaskId(), documentCount, bucketCount);
            if (syncAlias) {
                log.warn(message);
            } else {
                log.debug(message);
            }
        }
    }

    private void submitTask(ScrollerTask task, List<String> documentIds, int batchNumber, boolean syncAlias) {
        var batchTask = getBatchTask(task, documentIds, batchNumber);
        var properties = batchTask.getProperties();
        properties.put(PROPERTY_SYNC_ALIAS, syncAlias);
        properties.put(PROPERTY_REINDEXING_COMMAND,
                task.getProperties().getOrDefault(PROPERTY_REINDEXING_COMMAND, false));
        Framework.getService(TaskService.class).submitTask(batchTask);
    }

}
