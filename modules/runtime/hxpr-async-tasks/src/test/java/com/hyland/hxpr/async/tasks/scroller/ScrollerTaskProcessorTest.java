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
package com.hyland.hxpr.async.tasks.scroller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

import com.hyland.hxpr.async.tasks.AsyncTasksFeature;
import com.hyland.hxpr.async.tasks.message.ScrollerTask;

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

}
