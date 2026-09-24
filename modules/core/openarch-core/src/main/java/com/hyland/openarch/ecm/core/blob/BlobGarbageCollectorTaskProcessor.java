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

import static com.hyland.openarch.async.tasks.TaskStatus.State.COMPLETED;

import com.hyland.openarch.ecm.core.blob.binary.BinaryManagerStatus;
import com.hyland.openarch.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.openarch.async.tasks.TaskProcessor;
import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.async.tasks.message.RepositoryTask;

/**
 * Task processor running the blob garbage collector.
 * <p>
 * The deletion behavior is controlled by hardcoded constants. When recovery is enabled, deleted blobs are moved under
 * {@code .gcrecovery}; otherwise they and any existing recovery directory are permanently deleted.
 */
public class BlobGarbageCollectorTaskProcessor implements TaskProcessor<RepositoryTask> {

    private static final Logger log = LoggerFactory.getLogger(BlobGarbageCollectorTaskProcessor.class);

    public static final String BLOB_GC_TASK_NAME = "blobGarbageCollector";

    /** Whether to actually delete the garbage collected blobs. */
    static boolean DELETE = true;

    @Override
    public String getName() {
        return BLOB_GC_TASK_NAME;
    }

    @Override
    public Class<RepositoryTask> getTaskClass() {
        return RepositoryTask.class;
    }

    @Override
    public void process(RepositoryTask task) {
        String taskId = task.getTaskId();
        log.info("Starting blob GC task {} (delete={}, deleteWithRecovery={}, recoverRecovery={})", taskId, DELETE,
                AbstractBlobGarbageCollector.DELETE_WITH_RECOVERY, AbstractBlobGarbageCollector.RECOVER_RECOVERY);
        boolean success = true;
        String errorMessage = null;
        try {
            DocumentBlobService documentBlobService = Framework.getService(DocumentBlobService.class);
            BinaryManagerStatus status = documentBlobService.garbageCollectBinaries(DELETE);
            log.info("Completed blob GC task {} (delete={}, deleteWithRecovery={}, recoverRecovery={}): {}", taskId,
                    DELETE, AbstractBlobGarbageCollector.DELETE_WITH_RECOVERY,
                    AbstractBlobGarbageCollector.RECOVER_RECOVERY, status);
        } catch (RuntimeException e) {
            log.error("Failed blob GC task {}", taskId, e);
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            var asyncTasksService = Framework.getService(TaskService.class);
            var status = asyncTasksService.getStatus(taskId);
            if (success) {
                status.setState(COMPLETED);
            }
            status.setErrorMessage(errorMessage); // (also clears on success)
            asyncTasksService.setStatus(status);
        }
    }
}
