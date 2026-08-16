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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.nuxeo.ecm.core.blob.binary.BinaryManagerStatus;
import org.nuxeo.runtime.api.Framework;

import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.TaskStatus;
import com.hyland.hxpr.async.tasks.TaskStatus.State;
import com.hyland.hxpr.async.tasks.message.RepositoryTask;

/**
 * Unit tests for {@link BlobGarbageCollectorTaskProcessor}.
 */
public class TestBlobGarbageCollectorTaskProcessor {

    private static final String TASK_ID = "task-1";

    private final BlobGarbageCollectorTaskProcessor processor = new BlobGarbageCollectorTaskProcessor();

    @Test
    void testTaskProcessorBasicFunctionality() {
        assertEquals(BlobGarbageCollectorTaskProcessor.BLOB_GC_TASK_NAME, processor.getName());
        assertEquals(RepositoryTask.class, processor.getTaskClass());
    }

    @Test
    void testProcessRunsGCInNonDeleteMode() {
        DocumentBlobService documentBlobService = mock(DocumentBlobService.class);
        when(documentBlobService.garbageCollectBinaries(anyBoolean())).thenReturn(new BinaryManagerStatus());
        TaskService taskService = mock(TaskService.class);
        TaskStatus status = new TaskStatus(TASK_ID);
        when(taskService.getStatus(TASK_ID)).thenReturn(status);

        RepositoryTask task = mock(RepositoryTask.class);
        when(task.getTaskId()).thenReturn(TASK_ID);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(DocumentBlobService.class)).thenReturn(documentBlobService);
            framework.when(() -> Framework.getService(TaskService.class)).thenReturn(taskService);

            processor.process(task);
        }

        // GC must run in non-delete (dry-run) mode
        ArgumentCaptor<Boolean> deleteCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(documentBlobService).garbageCollectBinaries(deleteCaptor.capture());
        assertEquals(Boolean.FALSE, deleteCaptor.getValue());

        // The task status must be marked completed with no error
        assertEquals(State.COMPLETED, status.getState());
        assertNull(status.getErrorMessage());
        verify(taskService).setStatus(status);
    }

    @Test
    void testProcessRecordsErrorAndRethrowsOnFailure() {
        RuntimeException failure = new RuntimeException("boom");
        DocumentBlobService documentBlobService = mock(DocumentBlobService.class);
        when(documentBlobService.garbageCollectBinaries(anyBoolean())).thenThrow(failure);
        TaskService taskService = mock(TaskService.class);
        TaskStatus status = new TaskStatus(TASK_ID);
        when(taskService.getStatus(TASK_ID)).thenReturn(status);

        RepositoryTask task = mock(RepositoryTask.class);
        when(task.getTaskId()).thenReturn(TASK_ID);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(DocumentBlobService.class)).thenReturn(documentBlobService);
            framework.when(() -> Framework.getService(TaskService.class)).thenReturn(taskService);

            RuntimeException thrown = assertThrows(RuntimeException.class, () -> processor.process(task));
            assertSame(failure, thrown);
        }

        // On failure the status is not marked completed but the error message is recorded
        // (this is a mock so we don't observe RUNNING here - just that it isn't completed)
        assertNull(status.getState());
        assertEquals("boom", status.getErrorMessage());
        verify(taskService).setStatus(status);
    }

    @Test
    void testProcessNeverDeletesBlobs() {
        DocumentBlobService documentBlobService = mock(DocumentBlobService.class);
        when(documentBlobService.garbageCollectBinaries(anyBoolean())).thenReturn(new BinaryManagerStatus());
        TaskService taskService = mock(TaskService.class);
        when(taskService.getStatus(TASK_ID)).thenReturn(new TaskStatus(TASK_ID));

        RepositoryTask task = mock(RepositoryTask.class);
        when(task.getTaskId()).thenReturn(TASK_ID);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(DocumentBlobService.class)).thenReturn(documentBlobService);
            framework.when(() -> Framework.getService(TaskService.class)).thenReturn(taskService);

            processor.process(task);
        }

        // Only the dry-run call is ever made; delete mode is never triggered
        verify(documentBlobService).garbageCollectBinaries(false);
        verify(documentBlobService, never()).garbageCollectBinaries(true);
    }
}
