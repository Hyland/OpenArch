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

import static com.hyland.hxpr.async.tasks.TaskStatus.State.COMPLETED;
import static com.hyland.hxpr.async.tasks.executors.SetSystemPropertiesBatchTaskProcessor.BATCH_SET_SYSTEM_PROPERTIES_TASK_NAME;
import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.test.DocumentSetRepositoryInit.CREATED_TOTAL;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.PathRef;
import com.hyland.openarch.runtime.springboot.junit.RuntimeTestState;
import org.springframework.boot.test.context.SpringBootTest;

import com.hyland.hxpr.async.tasks.message.ScrollerTask;

@SpringBootTest(classes = RuntimeTestState.class)
public class TestSetSystemPropertiesTask extends AbstractTestAsyncTask {

    @Test
    public void testSetSystemProperties() throws Exception {

        DocumentModel model = session.getDocument(new PathRef("/default-domain/workspaces/test"));
        String nxql = "SELECT * FROM SysContent where sys_ancestorIds='%s'".formatted(model.getId());

        String commandId = asyncTasksService.submitTask(
                new ScrollerTask.Builder(BATCH_SET_SYSTEM_PROPERTIES_TASK_NAME, nxql, session.getRepositoryName(),
                        session.getPrincipal().getId()).param(SYS_IS_TRASHED, Boolean.TRUE).build());

        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(60)), "Bulk task didn't finish");

        TaskStatus status = asyncTasksService.getStatus(commandId);
        assertNotNull(status);

        List<TaskStatus> allTasks = ((TaskServiceImpl) asyncTasksService).getStatusesByTaskIdPrefix(commandId);
        // all tasks should have COMPLETED status and have processed all documents
        Assertions.assertTrue(allTasks.stream().allMatch(t -> t.getState() == COMPLETED));
        Assertions.assertEquals(CREATED_TOTAL, allTasks.stream().map(TaskStatus::getProcessed).mapToInt(Long::intValue).sum());

        txFeature.nextTransaction();

        for (DocumentModel child : session.getChildren(model.getRef())) {
            assertEquals(TRUE, child.getPropertyValue(SYS_IS_TRASHED));
        }
    }

    /**
     * The action must not completely fail even when setting a property fails (property not found or version not
     * writable).
     */
    @Test
    public void testSetSystemPropertiesFailures() throws Exception {

        DocumentModel doc = session.getDocument(new PathRef("/default-domain/workspaces/test/testdoc0"));
        DocumentRef verRef = session.checkIn(doc.getRef());
        DocumentModel ver = session.getDocument(verRef);

        assertFalse(doc.isTrashed());
        assertFalse(ver.isTrashed());

        session.save();
        txFeature.nextTransaction();

        String nxql = "SELECT * FROM SysContent WHERE sys_id in ( '%s', '%s')".formatted(ver.getId(), doc.getId());

        String commandId = asyncTasksService.submitTask(
                new ScrollerTask.Builder(BATCH_SET_SYSTEM_PROPERTIES_TASK_NAME, nxql, session.getRepositoryName(),
                        session.getPrincipal().getId()).param(SYS_IS_TRASHED, Boolean.TRUE).build());

        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(60)), "Bulk task didn't finish");

        List<TaskStatus> allTasks = ((TaskServiceImpl) asyncTasksService).getStatusesByTaskIdPrefix(commandId);
        // all tasks should have COMPLETED status and have processed all documents
        Assertions.assertTrue(allTasks.stream().allMatch(t -> t.getState() == COMPLETED));
        Assertions.assertEquals(2, allTasks.stream().map(TaskStatus::getProcessed).mapToInt(Long::intValue).sum());
    }
}
