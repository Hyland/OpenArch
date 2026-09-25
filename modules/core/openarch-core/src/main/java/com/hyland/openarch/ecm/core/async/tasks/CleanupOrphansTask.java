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
package com.hyland.openarch.ecm.core.async.tasks;

import static java.util.Objects.requireNonNullElse;
import static com.hyland.openarch.ecm.core.async.tasks.CleanupOrphansTaskProcessor.CLEANUP_ORPHANS_TASK_NAME;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hyland.openarch.async.tasks.message.AbstractTask;

import java.time.Duration;

public class CleanupOrphansTask extends AbstractTask {

    public static final int DEFAULT_BATCH_SIZE = 50;

    protected Long batchTransactionTimeout;

    protected int batchSize = DEFAULT_BATCH_SIZE;

    public CleanupOrphansTask() {
        // Empty constructor for deserialization
    }

    public CleanupOrphansTask(String repository, String username) {
        super(CLEANUP_ORPHANS_TASK_NAME, repository, username);
    }

    public CleanupOrphansTask(String repository, String username, Long batchTimeout) {
        super(CLEANUP_ORPHANS_TASK_NAME, repository, username);
        this.batchTransactionTimeout = batchTimeout;
    }

    @JsonIgnore
    public Duration getBatchTransactionTimeoutDuration() {
        return Duration.ofSeconds(requireNonNullElse(batchTransactionTimeout, 0L));
    }

    public int getBatchSize() {
        return batchSize;
    }
}
