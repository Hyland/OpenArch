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
package com.hyland.hxpr.async.tasks;

import java.time.Duration;

import org.nuxeo.common.utils.TooLargeException;

import com.hyland.hxpr.async.tasks.message.Task;

public interface TaskService {

    /**
     * Submits a task.
     * <p>
     * {@code throwOnError} allows synchronous checks of the task format. Note that further errors could be thrown
     * asynchronously (and thus only visible in logs) by the service itself, and therefore not detected directly even if
     * {@code throwOnError} is {@code true}.
     *
     * @param task the task
     * @param throwOnError whether to throw an exception if an error is detected synchronously
     * @throws TooLargeException if the task is too large and {@code throwOnError} is {@code true}
     */
    String submitTask(Task task, boolean throwOnError);

    /**
     * Submits a task.
     * <p>
     * Does not throw on task format errors.
     *
     * @param task the task
     */
    String submitTask(Task task);

    String setStatus(TaskStatus status);

    TaskStatus getStatus(String taskId);

    boolean awaitCompletion(Duration duration, String... taskNames) throws InterruptedException;

    /**
     * Service allowing to add extra info to the task message before sending it to the queue. This is useful for example
     * to add the node id in a cluster environment.
     */
    void addExtraInfoToTaskMessage(Task task);
}
