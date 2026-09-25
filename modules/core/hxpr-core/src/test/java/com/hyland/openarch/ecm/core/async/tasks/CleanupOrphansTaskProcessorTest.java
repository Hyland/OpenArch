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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.SessionImpl;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.model.InternalSession;
import com.hyland.openarch.runtime.api.Framework;

import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.async.tasks.TaskStatus;

/**
 * Unit tests for {@link CleanupOrphansTaskProcessor}, exercising {@code batchRemoveDocuments} directly with a mocked
 * {@link SessionImpl} to bypass the transaction/login wrapper.
 */
class CleanupOrphansTaskProcessorTest {

    private final CleanupOrphansTaskProcessor processor = new CleanupOrphansTaskProcessor();

    private static SessionImpl session(InternalSession internal, RepositoryPrincipal principal) {
        SessionImpl session = mock(SessionImpl.class);
        when(session.getSession()).thenReturn(internal);
        DocumentModel root = mock(DocumentModel.class);
        when(root.getId()).thenReturn("root");
        when(session.getRootDocument()).thenReturn(root);
        when(session.getPrincipal()).thenReturn(principal);
        return session;
    }

    private static CleanupOrphansTask task(int batchSize) {
        CleanupOrphansTask task = mock(CleanupOrphansTask.class);
        when(task.getTaskId()).thenReturn("tid");
        when(task.getBatchSize()).thenReturn(batchSize);
        return task;
    }

    @Test
    void nameAndTaskClass() {
        assertEquals(CleanupOrphansTaskProcessor.CLEANUP_ORPHANS_TASK_NAME, processor.getName());
        assertEquals(CleanupOrphansTask.class, processor.getTaskClass());
    }

    @Test
    void removesOrphansAndSkipsRootAndParentedDocuments() {
        InternalSession internal = mock(InternalSession.class);
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        SessionImpl session = session(internal, principal);
        when(session.exists(new IdRef("p-missing"))).thenReturn(false);
        when(session.exists(new IdRef("p-exists"))).thenReturn(true);

        TaskService taskService = mock(TaskService.class);
        TaskStatus status = mock(TaskStatus.class);
        when(taskService.getStatus("tid")).thenReturn(status);

        List<Map<String, Object>> rows = List.of( //
                Map.of("sys_id", "root", "sys_parentId", "none"), //
                Map.of("sys_id", "orphan", "sys_parentId", "p-missing"), //
                Map.of("sys_id", "kept", "sys_parentId", "p-exists"));

        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(TaskService.class)).thenReturn(taskService);
            processor.batchRemoveDocuments(session, rows, task(10));
        }

        verify(internal).removeDocument("orphan", principal);
        verify(internal, never()).removeDocument(eq("kept"), any());
        verify(internal, never()).removeDocument(eq("root"), any());
        verify(session).save();
        verify(status).setState(COMPLETED);
        verify(taskService).setStatus(status);
    }

    @Test
    void flushesWhenBatchSizeReached() {
        InternalSession internal = mock(InternalSession.class);
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        SessionImpl session = session(internal, principal);
        when(session.exists(new IdRef("p-missing"))).thenReturn(false);

        TaskService taskService = mock(TaskService.class);
        when(taskService.getStatus("tid")).thenReturn(mock(TaskStatus.class));

        List<Map<String, Object>> rows = List.of(Map.of("sys_id", "orphan", "sys_parentId", "p-missing"));

        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(TaskService.class)).thenReturn(taskService);
            processor.batchRemoveDocuments(session, rows, task(1)); // batch size 1 -> mid-loop flush
        }

        verify(internal).removeDocument("orphan", principal);
    }

    @Test
    void ignoresAlreadyDeletedDocuments() {
        InternalSession internal = mock(InternalSession.class);
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);
        SessionImpl session = session(internal, principal);
        when(session.exists(new IdRef("p-missing"))).thenReturn(false);
        doThrow(new DocumentNotFoundException("gone")).when(internal).removeDocument(eq("orphan"), any());

        TaskService taskService = mock(TaskService.class);
        when(taskService.getStatus("tid")).thenReturn(mock(TaskStatus.class));

        List<Map<String, Object>> rows = List.of(Map.of("sys_id", "orphan", "sys_parentId", "p-missing"));

        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(TaskService.class)).thenReturn(taskService);
            assertDoesNotThrow(() -> processor.batchRemoveDocuments(session, rows, task(10)));
        }

        verify(session).save();
    }
}
