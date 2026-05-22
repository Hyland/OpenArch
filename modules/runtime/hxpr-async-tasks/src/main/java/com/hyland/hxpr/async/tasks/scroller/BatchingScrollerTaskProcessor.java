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
package com.hyland.hxpr.async.tasks.scroller;

import java.util.List;

import org.nuxeo.runtime.api.Framework;

import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.message.BatchTask;
import com.hyland.hxpr.async.tasks.message.ScrollerTask;

public class BatchingScrollerTaskProcessor extends AbstractScrollerTaskProcessor {

    public static final String BATCHING_SCROLLER_TASK_NAME = "scroller";

    @Override
    public String getName() {
        return BATCHING_SCROLLER_TASK_NAME;
    }

    @Override
    protected void process(ScrollerTask task, List<String> documentIds, int batchNumber) {
        Framework.getService(TaskService.class).submitTask(getBatchTask(task, documentIds, batchNumber));
    }

    protected BatchTask getBatchTask(ScrollerTask task, List<String> documentIds, int batchNumber) {
        return new BatchTask.Builder(task.getBulkTaskName(), task.getRepository(), task.getUsername(),
                task.getBatchSize()).withTaskId("%s-%d".formatted(task.getTaskId(), batchNumber))
                                    .withNodeId(task.getNodeId())
                                    .withTimestamp(task.getTimestamp())
                                    .withProperties(task.getProperties())
                                    .withDocumentIds(documentIds)
                                    .build();
    }

}
