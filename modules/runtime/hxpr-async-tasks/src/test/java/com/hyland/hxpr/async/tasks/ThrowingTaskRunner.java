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

import com.hyland.hxpr.async.tasks.message.Task;
import com.hyland.openarch.common.utils.BatchUtils;
import com.hyland.openarch.common.utils.TooLargeException;
import com.hyland.openarch.ecm.core.api.NuxeoException;

/**
 * Fake {@link TaskRunner} used to test how {@link TaskServiceImpl#submitTask} reacts to failures raised
 * synchronously by the runner, in particular whether {@link TooLargeException} is
 * propagated unwrapped (as required by {@link BatchUtils#tryWithSplit}) or swallowed into a
 * generic {@link NuxeoException}.
 * <p>
 * Injected directly into {@link TaskServiceImpl}'s internal runner registry by tests (via reflection), replacing
 * the runner otherwise bound to a task by the {@code runner} extension point. The exception to raise is configured
 * through the static {@link #exceptionToThrow} field.
 */
public class ThrowingTaskRunner<T extends Task> implements TaskRunner<T> {

    /** Exception raised by {@link #submitTask}, or {@code null} to behave as a no-op (success). */
    public static volatile RuntimeException exceptionToThrow;

    @Override
    public void initTaskProcessor(TaskProcessor<T> processor) {
        // no-op: this fake runner never dispatches to a real processor
    }

    @Override
    public void submitTask(String key, String message, boolean throwOnError) {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
    }

    @Override
    public void stopRunner() {
        // no-op
    }
}
