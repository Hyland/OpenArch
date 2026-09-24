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
package com.hyland.openarch.async.tasks;

import com.hyland.openarch.async.tasks.message.DocumentTask;
import com.hyland.openarch.ecm.core.api.RepositoryException;

public class FailTaskProcessor implements TaskProcessor<DocumentTask> {

    public static final String FAIL_TASK_NAME = "fail";

    private static final int MAX_FAILURE = 3;

    protected int count;

    @Override
    public String getName() {
        return FAIL_TASK_NAME;
    }

    @Override
    public Class<DocumentTask> getTaskClass() {
        return DocumentTask.class;
    }

    @Override
    public void process(DocumentTask task) {
        if (count++ < MAX_FAILURE) {
            throw new RepositoryException("Simulated exception for test purpose");
        }
        count = 0;
    }
}
