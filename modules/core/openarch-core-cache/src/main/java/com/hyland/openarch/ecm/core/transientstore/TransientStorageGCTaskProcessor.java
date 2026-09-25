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
package com.hyland.openarch.ecm.core.transientstore;

import static com.hyland.openarch.async.tasks.TaskStatus.State.COMPLETED;

import com.hyland.openarch.async.tasks.message.RepositoryTask;
import com.hyland.openarch.ecm.core.transientstore.api.TransientStoreService;
import com.hyland.openarch.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.openarch.async.tasks.TaskProcessor;
import com.hyland.openarch.async.tasks.TaskService;

public class TransientStorageGCTaskProcessor implements TaskProcessor<RepositoryTask> {

    private static final Logger log = LoggerFactory.getLogger(TransientStorageGCTaskProcessor.class);

    public final static String TRANSIENT_STORAGE_GC_TASK_NAME = "transientStorageGC";

    @Override
    public String getName() {
        return TRANSIENT_STORAGE_GC_TASK_NAME;
    }

    @Override
    public Class<RepositoryTask> getTaskClass() {
        return RepositoryTask.class;
    }

    @Override
    public void process(RepositoryTask task) {
        long startTime = System.currentTimeMillis();
        log.debug("Starting transient storage garbage collection task: {}", task.getTaskId());

        TransientStoreService tss = Framework.getService(TransientStoreService.class);
        String errorMessage = null;
        try {
            tss.doGC();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Transient storage garbage collection completed in {} ms", duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Transient storage garbage collection failed after {} ms", duration, e);
            errorMessage = e.getMessage();
            throw e;
        } finally {
            // Update the status of the GC task
            var asyncTasksService = Framework.getService(TaskService.class);
            var status = asyncTasksService.getStatus(task.getTaskId());
            status.setState(COMPLETED);
            if (errorMessage != null) {
                status.setErrorMessage(errorMessage);
            }
            asyncTasksService.setStatus(status);
        }
    }
}
