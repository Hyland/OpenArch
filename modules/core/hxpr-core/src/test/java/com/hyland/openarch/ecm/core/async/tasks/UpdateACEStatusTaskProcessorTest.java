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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.IterableQueryResult;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.api.login.LoginContext;

import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.async.tasks.TaskStatus;
import com.hyland.openarch.async.tasks.message.RepositoryTask;

class UpdateACEStatusTaskProcessorTest {

    private final UpdateACEStatusTaskProcessor processor = new UpdateACEStatusTaskProcessor();

    @Test
    void nameAndTaskClass() {
        assertEquals(UpdateACEStatusTaskProcessor.UPDATE_ACE_STATUS_TASK_NAME, processor.getName());
        assertEquals(RepositoryTask.class, processor.getTaskClass());
    }

    @Test
    void queryBuildsAStatement() {
        assertNotNull(UpdateACEStatusTaskProcessor.query(Instant.now()));
    }

    @Test
    void processCompletesTaskWhenRepositoryIsNull() {
        RepositoryTask task = mock(RepositoryTask.class);
        when(task.getProperties()).thenReturn(Map.of());
        when(task.getTaskId()).thenReturn("tid");
        when(task.getUsername()).thenReturn("user");
        when(task.getRepository()).thenReturn(null); // no repository -> no session work

        TaskService taskService = mock(TaskService.class);
        TaskStatus status = mock(TaskStatus.class);
        when(taskService.getStatus("tid")).thenReturn(status);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(TaskService.class)).thenReturn(taskService);
            framework.when(() -> Framework.loginUserOrSystem("user")).thenReturn(mock(LoginContext.class));

            processor.process(task);
        }

        verify(status).setState(COMPLETED);
        verify(taskService).setStatus(status);
    }

    @Test
    void processUpdatesAcesWhenSessionPresent() {
        RepositoryTask task = mock(RepositoryTask.class);
        when(task.getProperties()).thenReturn(Map.of());
        when(task.getTaskId()).thenReturn("tid");
        when(task.getUsername()).thenReturn("user");
        when(task.getRepository()).thenReturn("repo");

        TaskService taskService = mock(TaskService.class);
        TaskStatus status = mock(TaskStatus.class);
        when(taskService.getStatus("tid")).thenReturn(status);

        CoreSession session = mock(CoreSession.class);
        IterableQueryResult result = mock(IterableQueryResult.class);
        Map<String, Object> row = new HashMap<>();
        row.put("sys_id", "doc1");
        row.put("sys_acl/*1/principal", "alice");
        row.put("sys_acl/*1/permission", "Read");
        row.put("sys_acl/*1/grant", Boolean.TRUE);
        row.put("sys_acl/*1/creator", "admin");
        row.put("sys_acl/*1/begin", Instant.now());
        row.put("sys_acl/*1/end", Instant.now());
        when(result.iterator()).thenReturn(List.of(row).iterator());
        when(session.queryAndFetch(any(Statement.class))).thenReturn(result);
        DocumentModel doc = mock(DocumentModel.class);
        ACL acl = mock(ACL.class);
        when(session.getDocument(any(DocumentRef.class))).thenReturn(doc);
        when(doc.getLocalACL()).thenReturn(acl);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class);
                MockedStatic<CoreInstance> coreInstance = mockStatic(CoreInstance.class)) {
            framework.when(() -> Framework.getService(TaskService.class)).thenReturn(taskService);
            framework.when(() -> Framework.loginUserOrSystem("user")).thenReturn(mock(LoginContext.class));
            coreInstance.when(() -> CoreInstance.getCoreSession("repo")).thenReturn(session);

            processor.process(task);
        }

        verify(doc).setACL(acl, true);
        verify(session).save();
        verify(status).addProcessed(1L);
        verify(status).setState(COMPLETED);
        verify(result).close();
    }

    @Test
    void processReadsTimeoutProperty() {
        RepositoryTask task = mock(RepositoryTask.class);
        when(task.getProperties()).thenReturn(Map.of(UpdateACEStatusTaskProcessor.TIMEOUT_PROPERTY, 5L));
        when(task.getTaskId()).thenReturn("tid");
        when(task.getUsername()).thenReturn("user");
        when(task.getRepository()).thenReturn(null);

        TaskService taskService = mock(TaskService.class);
        TaskStatus status = mock(TaskStatus.class);
        when(taskService.getStatus("tid")).thenReturn(status);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(TaskService.class)).thenReturn(taskService);
            framework.when(() -> Framework.loginUserOrSystem("user")).thenReturn(mock(LoginContext.class));

            processor.process(task);
        }

        verify(status).setState(COMPLETED);
        verify(taskService).setStatus(status);
    }
}
