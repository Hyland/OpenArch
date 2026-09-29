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
package com.hyland.openarch.async.tasks.scroller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.ScrollResultImpl;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.api.login.LoginService;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

import com.hyland.openarch.async.tasks.AsyncTasksFeature;
import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.async.tasks.TaskStatus;
import com.hyland.openarch.async.tasks.message.ScrollerTask;

@ExtendWith(FeaturesExtension.class)
@Features(AsyncTasksFeature.class)
public class ScrollerTaskProcessorTest {

    @Test
    public void testBatchTask() {
        var taskProcessor = new BatchingScrollerTaskProcessor();
        var scrollerTask = new ScrollerTask.Builder("testTask", "SELECT * FROM SysContent", "test",
                "bob").withBatchSize(50).build();
        var taskBatch = taskProcessor.getBatchTask(scrollerTask, List.of("uuid1", "uuid2"), 1);
        assertThat(taskBatch.getTaskName()).isEqualTo("testTask");
        assertThat(taskBatch.getDocumentIds()).containsExactlyInAnyOrder("uuid1", "uuid2");
        assertThat(taskBatch.getBatchSize()).isEqualTo(50);
        assertThat(taskBatch.getTimestamp()).isEqualTo(scrollerTask.getTimestamp());
        assertThat(taskBatch.getNodeId()).isEqualTo(scrollerTask.getNodeId());
        assertThat(taskBatch.getTaskId()).isEqualTo(scrollerTask.getTaskId() + "-1");
    }

    /**
     * Regression test for the fix restoring the ordering of the terminal {@code COMPLETED} status persistence
     * relative to {@link AbstractScrollerTaskProcessor#handleFinalProcessing}.
     * <p>
     * The scroller query is picked so it matches zero documents, since that path still exercises the exact
     * sequence under test: {@code handlePostProcessing} -&gt; {@code status.setState(COMPLETED)} -&gt;
     * {@code finally} -&gt; {@code asyncTasksService.setStatus(status)} -&gt; {@code handleFinalProcessing}. The
     * underlying {@link CoreSession} is mocked (with a static mock of {@link CoreInstance}) since this module has
     * no dependency on a real OpenArch core/repository test harness.
     * <p>
     * The assertion fetches the status fresh from {@link TaskService#getStatus(String)} from within
     * {@code handleFinalProcessing} itself, rather than relying on the local {@code status} variable held by
     * {@link AbstractScrollerTaskProcessor#process(ScrollerTask)}, so it actually proves the status was persisted
     * before {@code handleFinalProcessing} ran.
     */
    @Test
    public void handleFinalProcessingObservesAlreadyPersistedCompletedStatus() {
        var taskService = Framework.getService(TaskService.class);
        var task = new ScrollerTask.Builder("testTask", "SELECT * FROM SysContent WHERE sys_id = 'no-such-doc'",
                "test", LoginService.SYSTEM_USERNAME).withBatchSize(50).build();

        var status = new TaskStatus(task.getTaskId());
        status.setUsername(task.getUsername());
        status.setState(TaskStatus.State.SCHEDULED);
        status.setTask(task.getTaskId());
        status.setSubmitTime(Instant.now());
        taskService.setStatus(status);

        CoreSession session = mock(CoreSession.class);
        when(session.scroll(eq(task.getQuery()), eq(task.getBatchSize()), anyInt())).thenReturn(ScrollResultImpl.emptyResult());

        var sut = new RecordingScrollerTaskProcessor();
        try (MockedStatic<CoreInstance> coreInstance = mockStatic(CoreInstance.class)) {
            coreInstance.when(() -> CoreInstance.getCoreSessionSystem(task.getRepository())).thenReturn(session);

            sut.process(task);
        }

        assertThat(sut.observedStateDuringFinalProcessing).isEqualTo(TaskStatus.State.COMPLETED);
        assertThat(taskService.getStatus(task.getTaskId()).getState()).isEqualTo(TaskStatus.State.COMPLETED);
    }

    private static final class RecordingScrollerTaskProcessor extends AbstractScrollerTaskProcessor {

        private TaskStatus.State observedStateDuringFinalProcessing;

        @Override
        public String getName() {
            return "recordingScrollerTaskProcessor";
        }

        @Override
        protected void process(ScrollerTask task, List<String> documentIds, int batchNumber) {
            // no-op
        }

        @Override
        protected void handleFinalProcessing(ScrollerTask task, int documentCount, int bucketCount) {
            var taskService = Framework.getService(TaskService.class);
            observedStateDuringFinalProcessing = taskService.getStatus(task.getTaskId()).getState();
        }
    }

}
