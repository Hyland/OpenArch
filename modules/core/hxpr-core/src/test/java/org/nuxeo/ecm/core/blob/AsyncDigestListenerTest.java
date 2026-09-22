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
package org.nuxeo.ecm.core.blob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.nuxeo.ecm.core.async.tasks.ComputeDigestTask;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.services.event.Event;

import com.hyland.hxpr.async.tasks.TaskService;

class AsyncDigestListenerTest {

    @Test
    void handleEventSubmitsComputeDigestTaskFromEventData() {
        Event event = new Event("blob", "digest", null, Map.of("blobProviderId", "bp", "key", "k"));
        TaskService taskService = mock(TaskService.class);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(TaskService.class)).thenReturn(taskService);

            new AsyncDigestListener().handleEvent(event);
        }

        ArgumentCaptor<ComputeDigestTask> captor = ArgumentCaptor.forClass(ComputeDigestTask.class);
        verify(taskService).submitTask(captor.capture());
        assertEquals("bp", captor.getValue().getBlobProviderId());
        assertEquals("k", captor.getValue().getKey());
    }
}
