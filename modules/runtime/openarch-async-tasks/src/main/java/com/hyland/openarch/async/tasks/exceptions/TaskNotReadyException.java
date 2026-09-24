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
package com.hyland.openarch.async.tasks.exceptions;

import com.hyland.openarch.ecm.core.api.RepositoryException;

/**
 * Exception thrown when a task is not ready to be executed compared with the linked cache invalidation message.
 * This can happen when the task is created on a different node and the cache invalidation message has not been
 * processed yet. It is caught when the async task runner checks if the received task is ready to be executed.
 * When it is thrown, the task message is not processed and remains in the queue for later processing.
 */
public class TaskNotReadyException extends RepositoryException {

    public TaskNotReadyException(String taskId, Long timestamp, Long latestTimestamp) {
        super("Task %s %s is not ready for this node. The latest cache invalidation timestamp received is %s"
                .formatted(taskId, timestamp, latestTimestamp));
    }
}
