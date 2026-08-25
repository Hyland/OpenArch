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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.common.utils.TooLargeException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

import com.hyland.hxpr.async.tasks.message.AbstractTask;

/**
 * Regression test for the fix restoring propagation of {@link TooLargeException} out of
 * {@link TaskServiceImpl#submitTask}.
 * <p>
 * {@link org.nuxeo.common.utils.BatchUtils#tryWithSplit} relies on catching {@link TooLargeException} <em>by
 * type</em> to split an oversized batch and retry. A defensive {@code catch (Exception e)} block that rewraps
 * every failure into a {@link NuxeoException} (added for HXCS-8351) silently defeated that mechanism, letting
 * SNS "message too large" failures escape all the way up instead of being retried with a smaller batch
 * (HXCS-8685).
 * <p>
 * The default {@code mem} runner is contributed as a singleton and captured once when the component starts, so it
 * cannot be swapped afterwards through an additional test-only OSGi contribution. Instead, a {@link ThrowingTaskRunner}
 * fake is injected directly into {@link TaskServiceImpl}'s internal runner registry via reflection, replacing the
 * runner bound to the existing {@code dummyTask} task.
 */
@ExtendWith(FeaturesExtension.class)
@Features(AsyncTasksFeature.class)
@Deploy("com.hyland.hxpr.async.tasks.tests:OSGI-INF/test-async-tasks-contrib.xml")
class TaskServiceImplTest {

    private static final class TestTask extends AbstractTask {

        TestTask() {
            // null taskId lets AbstractTask generate one in the "<taskName>:<uuid>" format used in production;
            // a bare UUID would instead hit TaskServiceImpl#getStatus's legacy-format lookup branch and never
            // match the plain key written by #setStatus.
            super(null, DummyBatchTaskProcessor.DUMMY_TASK_NAME, null, "Administrator", "node1", null);
        }
    }

    @BeforeEach
    void injectThrowingRunner() throws ReflectiveOperationException {
        var service = (TaskServiceImpl) Framework.getService(TaskService.class);
        Field runnersField = TaskServiceImpl.class.getDeclaredField("runners");
        runnersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var runners = (Map<String, TaskRunner<?>>) runnersField.get(service);
        runners.put(DummyBatchTaskProcessor.DUMMY_TASK_NAME, new ThrowingTaskRunner<>());
    }

    @AfterEach
    void resetThrowingRunner() {
        ThrowingTaskRunner.exceptionToThrow = null;
    }

    @Test
    void shouldPropagateTooLargeExceptionUnwrappedSoThatItCanBeSplitAndRetried() {
        // given a runner that rejects the message as too large, as AWSSNSServiceImpl does for oversized SNS payloads
        ThrowingTaskRunner.exceptionToThrow = new TooLargeException("message too large");
        TaskService taskService = Framework.getService(TaskService.class);
        var task = new TestTask();

        // when submitting with throwOnError=true
        // then the original TooLargeException must escape unwrapped, so callers such as
        // BatchUtils#tryWithSplit can catch it by type and retry with a smaller batch
        assertThatThrownBy(() -> taskService.submitTask(task, true)) //
                .isInstanceOf(TooLargeException.class)
                .hasMessage("message too large");

        var status = taskService.getStatus(task.getTaskId());
        assertThat(status.getState()).isEqualTo(TaskStatus.State.ABORTED);
        assertThat(status.getErrorMessage()).isEqualTo("message too large");
    }

    @Test
    void shouldStillWrapUnrelatedExceptionsInNuxeoException() {
        // given a runner that fails for a reason unrelated to message size
        ThrowingTaskRunner.exceptionToThrow = new IllegalStateException("broker unavailable");
        TaskService taskService = Framework.getService(TaskService.class);
        var task = new TestTask();

        // when / then the pre-existing NuxeoException wrapping behavior is preserved for every other exception type
        assertThatThrownBy(() -> taskService.submitTask(task, true)) //
                .isInstanceOf(NuxeoException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        var status = taskService.getStatus(task.getTaskId());
        assertThat(status.getState()).isEqualTo(TaskStatus.State.ABORTED);
        assertThat(status.getErrorMessage()).isEqualTo("broker unavailable");
    }
}
