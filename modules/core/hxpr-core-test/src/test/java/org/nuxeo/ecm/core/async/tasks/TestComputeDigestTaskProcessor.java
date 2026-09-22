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
package org.nuxeo.ecm.core.async.tasks;

import static com.hyland.hxpr.async.tasks.TaskStatus.State.COMPLETED;
import static com.hyland.hxpr.async.tasks.TaskStatus.State.SCHEDULED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import com.google.inject.Inject;
import com.hyland.hxpr.async.tasks.AsyncTasksFeature;
import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.TaskStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

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

        assertThrows(NuxeoException.class, () -> sut.process(task));
    }
}
