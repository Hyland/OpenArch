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
package com.hyland.hxpr.async.tasks;

import org.nuxeo.common.utils.TooLargeException;

import com.hyland.hxpr.async.tasks.message.Task;

/**
 * A TaskRunner is started when then BulkAction component starts.
 */
public interface TaskRunner<T extends Task> {

    void initTaskProcessor(TaskProcessor<T> processor);

    /**
     * Submits a task.
     * <p>
     * {@code throwOnError} allows synchronous checks of the message format. Note that further errors could be thrown
     * asynchronously (and thus only visible in logs) by the service itself, and therefore not detected directly even if
     * {@code throwOnError} is {@code true}.
     *
     * @param key the task key
     * @param message the task message
     * @param throwOnError whether to throw an exception if an error is detected synchronously
     * @throws TooLargeException if the message is too large and {@code throwOnError} is {@code true}
     */
    void submitTask(String key, String message, boolean throwOnError);

    void stopRunner();

}
