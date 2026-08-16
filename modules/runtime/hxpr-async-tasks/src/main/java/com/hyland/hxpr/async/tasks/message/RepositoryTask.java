/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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

/**
 * A task related to a repository but not to a specific document.
 */
public class RepositoryTask extends AbstractTask {

    public RepositoryTask() {
        // Empty constructor for deserialization
    }

    public RepositoryTask(String taskName, String repository, String username) {
        super(taskName, repository, username);
        // The timestamp is always null for a repository task
        this.timestamp = null;
    }

    // copy of BlobGarbageCollectorTaskProcessor.BLOB_GC_TASK_NAME
    private static final String BLOB_GC_TASK_NAME = "blobGarbageCollector";

    @Override
    public boolean requiresRunningGuard() {
        // easier than having a separate variant of RepositoryTask just to override this
        // blob GC is long-running so requires a guard
        return BLOB_GC_TASK_NAME.equals(taskName);
    }

}
