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

import static com.hyland.openarch.async.tasks.DummyBatchTaskProcessor.DUMMY_TASK_NAME;
import static com.hyland.openarch.async.tasks.TaskStatus.State.COMPLETED;
import static com.hyland.openarch.async.tasks.TaskStatus.State.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.springboot.junit.RuntimeTestState;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;

import com.hyland.openarch.async.tasks.mem.MemTaskRunner;
import com.hyland.openarch.async.tasks.message.DocumentTask;

@SpringBootTest(classes = RuntimeTestState.class)
@Features(AsyncTasksFeature.class)
@Deploy("com.hyland.openarch.async.tasks.tests:OSGI-INF/test-async-tasks-contrib.xml")
public class TestAsyncTasksServiceAdmin extends RuntimeSpringBootTest {

    @RuntimeInject
    private TaskService asyncTasksService;

    @Test
    public void serviceCanDefineAsyncTasksRunner() {
        assertThat(((TaskServiceImpl) asyncTasksService).getRunner(DUMMY_TASK_NAME)).isInstanceOf(MemTaskRunner.class);
    }

    @Test
    public void serviceChecksIfTaskAvailable() {
        assertThat(((TaskServiceImpl) asyncTasksService).isTaskAvailable(DUMMY_TASK_NAME)).isTrue();
        assertThat(((TaskServiceImpl) asyncTasksService).isTaskAvailable("unknown")).isFalse();
    }

    @Test
    public void waitTasksCompletionWithTaskStatuses() throws InterruptedException {
        var now = Instant.now();

        var task01 = new DocumentTask("task01", null, "test", null);
        var task02 = new DocumentTask("task02", null, "test", null);
        var task03 = new DocumentTask("task03", null, "test", null);

        var status01 = new TaskStatus();
        status01.setId(task01.getTaskId());
        status01.setSubmitTime(now);
        status01.setTask("task01");
        status01.setState(RUNNING);
        var status02 = new TaskStatus();
        status02.setId(task02.getTaskId());
        status02.setSubmitTime(now);
        status02.setTask("task02");
        status02.setState(RUNNING);
        var status03 = new TaskStatus();
        status03.setId(task03.getTaskId());
        status03.setSubmitTime(now);
        status03.setTask("task03");
        status03.setState(COMPLETED);
        asyncTasksService.setStatus(status01);
        asyncTasksService.setStatus(status02);
        asyncTasksService.setStatus(status03);

        var deadline = System.nanoTime() + Duration.ofMillis(100).toNanos();
        assertThat(((TaskServiceImpl) asyncTasksService).isTasksCompleted(List.of(status01), deadline)).isFalse();
        assertThat(((TaskServiceImpl) asyncTasksService).isTasksCompleted(List.of(status02), deadline)).isFalse();
        assertThat(((TaskServiceImpl) asyncTasksService).isTasksCompleted(List.of(status03), deadline)).isTrue();
        assertThat(((TaskServiceImpl) asyncTasksService).isTasksCompleted(List.of(status02, status03), deadline)).isFalse();
    }

    @Test
    public void defaultServiceDoesNotAddInfo() {
        var task = new  DocumentTask("task", "default", "test", "docid", null);
        asyncTasksService.addExtraInfoToTaskMessage(task);
        assertThat(task.getNodeId()).isNull();
    }
}
