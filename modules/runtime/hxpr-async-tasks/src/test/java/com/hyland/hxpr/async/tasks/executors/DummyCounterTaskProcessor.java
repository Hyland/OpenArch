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
package com.hyland.hxpr.async.tasks.executors;

import static com.hyland.hxpr.async.tasks.TaskStatus.State.COMPLETED;

import com.hyland.hxpr.async.tasks.TaskProcessor;
import com.hyland.hxpr.async.tasks.TaskService;
import org.nuxeo.runtime.api.Framework;

import java.util.concurrent.atomic.AtomicInteger;

public class DummyCounterTaskProcessor implements TaskProcessor<DummyTask> {

    private static final AtomicInteger retryCount = new AtomicInteger(0);

    public static final String DUMMY_COUNTER_TASK_NAME = "dummyTask";

    public static int getRetryCount() {
        return retryCount.get();
    }

    @Override
    public String getName() {
        return DUMMY_COUNTER_TASK_NAME;
    }

    @Override
    public Class<DummyTask> getTaskClass() {
        return DummyTask.class;
    }

    @Override
    public void process(DummyTask task) {
        retryCount.incrementAndGet();
        var asyncTasksService = Framework.getService(TaskService.class);
        var status = asyncTasksService.getStatus(task.getTaskId());
        status.setState(COMPLETED);
        asyncTasksService.setStatus(status);
    }
}
