/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.blob;

import static com.hyland.openarch.ecm.core.blob.BlobGarbageCollectorTaskProcessor.BLOB_GC_TASK_NAME;

import com.hyland.openarch.ecm.core.scheduler.ScheduledJob;
import com.hyland.openarch.runtime.api.Framework;

import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.message.RepositoryTask;

/**
 * Scheduled job periodically triggering the blob garbage collector.
 * <p>
 * The actual work is offloaded to an asynchronous task so it does not block a scheduler thread.
 */
public class BlobGarbageCollectorScheduledJob implements ScheduledJob {

    @Override
    public void run() {
        TaskService taskService = Framework.getService(TaskService.class);
        // The GC is global (all repositories), so the task has no specific repository and no user.
        // The timestamp is forced to null as it is not related to a cache invalidation.
        taskService.submitTask(new RepositoryTask(BLOB_GC_TASK_NAME, null, null));
    }
}
