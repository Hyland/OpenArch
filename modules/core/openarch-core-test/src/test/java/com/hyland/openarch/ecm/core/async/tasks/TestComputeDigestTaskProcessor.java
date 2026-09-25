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
package com.hyland.openarch.ecm.core.async.tasks;

import static com.hyland.openarch.async.tasks.TaskStatus.State.COMPLETED;
import static com.hyland.openarch.async.tasks.TaskStatus.State.SCHEDULED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import com.google.inject.Inject;
import com.hyland.openarch.async.tasks.AsyncTasksFeature;
import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.async.tasks.TaskStatus;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features({ CoreFeature.class, AsyncTasksFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.core.test:OSGI-INF/test-storage-blobstore-contrib.xml")
public class TestComputeDigestTaskProcessor {

    ComputeDigestTaskProcessor sut = new ComputeDigestTaskProcessor();

    @Inject
    TaskService taskService;

    @Test
    public void processCompletesTaskWhenBlobMissing() {
        ComputeDigestTask task = new ComputeDigestTask("test", "nosuchkey");
        TaskStatus status = new TaskStatus(task.getTaskId());
        status.setState(SCHEDULED);
        status.setTask(task.getTaskId());
        status.setSubmitTime(Instant.now());
        taskService.setStatus(status);

        sut.process(task);

        assertEquals(COMPLETED, taskService.getStatus(task.getTaskId()).getState());
    }

    @Test
    public void processRethrowsForUnknownBlobProvider() {
        ComputeDigestTask task = new ComputeDigestTask("nosuchprovider", "key");

        assertThrows(RepositoryException.class, () -> sut.process(task));
    }
}
