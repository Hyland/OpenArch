/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
 *
 * Contributors:
 *     Gildas Lefevre
 */
package com.hyland.hxpr.async.tasks;

import static com.hyland.hxpr.async.tasks.TaskStatus.State.COMPLETED;
import static com.hyland.hxpr.async.tasks.executors.SetPropertiesBatchTaskProcessor.BATCH_SET_PROPERTIES_TASK_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static org.nuxeo.ecm.core.test.DocumentSetRepositoryInit.USERNAME;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.runtime.springboot.junit.RuntimeTestState;
import org.springframework.boot.test.context.SpringBootTest;

import com.hyland.hxpr.async.tasks.message.ScrollerTask;

@SpringBootTest(classes = RuntimeTestState.class)
public class TestSetPropertiesTask extends AbstractTestAsyncTask {

    @Test
    public void testSetPropertiesAsAdmin() throws Exception {
        testSetProperties(TEST_ADMIN_USER);
    }

    @Test
    public void testSetPropertiesAsNonAdmin() throws Exception {
        testSetProperties(USERNAME);
    }

    protected void testSetProperties(String username) throws Exception {
        DocumentModel model = session.getDocument(new PathRef("/default-domain/workspaces/test"));
        String nxql = "SELECT * FROM SysContent where sys_parentId='%s'".formatted(model.getId());

        String title = "test title";
        String description = "test description";
        String foo = "test foo";
        String bar = "test bar";

        HashMap<String, Serializable> complex = new HashMap<>();
        complex.put("foo", foo);
        complex.put("bar", bar);

        List<TaskStatus> oldTasks = getStatuses(username);

        String commandId = asyncTasksService.submitTask(
                new ScrollerTask.Builder(BATCH_SET_PROPERTIES_TASK_NAME, nxql, session.getRepositoryName(), username).param(
                        SYS_TITLE, title).param(SYS_DESCRIPTION, description).param("cpx_complex", complex).build());

        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(60))); // "Bulk action didn't finish"

        TaskStatus status = asyncTasksService.getStatus(commandId);
        assertNotNull(status);
        assertEquals(COMPLETED, status.getState());

        // remove old tasks that may be present in the list of statuses
        List<TaskStatus> statuses = getStatuses(username).stream()
                                                         .filter(s -> oldTasks.stream()
                                                                              .noneMatch(old -> old.getId()
                                                                                                   .equals(s.getId())))
                                                         .toList();
        // 2 TaskStatus are created: scroller and batch task
        assertEquals(2, statuses.size());
        // check that the taskid of all TaskStatuses in statuses begins status.getId()
        assertThat(statuses).extracting(TaskStatus::getId).allMatch(taskId -> taskId.startsWith(status.getId()));

        List<TaskStatus> emptyStatuses = getStatuses("toto");
        assertEquals(0, emptyStatuses.size());

        txFeature.nextTransaction();

        for (DocumentModel child : session.query(nxql)) {
            assertEquals(title, child.getTitle());
            assertEquals(description, child.getPropertyValue(SYS_DESCRIPTION));
            if (child.getType().equals("ComplexDoc")) {
                assertEquals(foo, child.getPropertyValue("cpx_complex/foo"));
                assertEquals(bar, child.getPropertyValue("cpx_complex/bar"));
            }
        }
    }

    private List<TaskStatus> getStatuses(String username) {
        return ((TaskServiceImpl) asyncTasksService).getStatuses(username);
    }

    /**
     * The action must not completely fail even when setting a property fails (property not found or version not
     * writable).
     */
    @Test
    public void testSetPropertiesFailures() throws Exception {
        DocumentModel workspace = session.getDocument(new PathRef("/default-domain/workspaces/test"));
        DocumentModel doc = session.getDocument(new PathRef("/default-domain/workspaces/test/testdoc0"));
        DocumentRef verRef = session.checkIn(doc.getRef());
        DocumentModel ver = session.getDocument(verRef);
        session.save();
        txFeature.nextTransaction();

        String nxql = "SELECT * FROM SysContent WHERE sys_id in ('%s', '%s', '%s')".formatted(workspace.getId(), // no
                                                                                                                 // such
                                                                                                                 // property
                ver.getId(), // not writable
                doc.getId() // ok
        );

        int oldSize = getStatuses(session.getPrincipal().getId()).size();

        String commandId = asyncTasksService.submitTask(
                new ScrollerTask.Builder(BATCH_SET_PROPERTIES_TASK_NAME, nxql, session.getRepositoryName(),
                        session.getPrincipal().getId()).param("cpx_complex/foo", "test foo").build());

        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(60))); // Bulk action didn't finish

        TaskStatus status = asyncTasksService.getStatus(commandId);
        assertNotNull(status);
        assertEquals(COMPLETED, status.getState());
        List<TaskStatus> allTasks = ((TaskServiceImpl) asyncTasksService).getStatusesByTaskIdPrefix(commandId);
        assertEquals(3, allTasks.stream().map(TaskStatus::getProcessed).mapToInt(Long::intValue).sum());

        List<TaskStatus> statuses = getStatuses(session.getPrincipal().getId());
        assertEquals(2, statuses.size() - oldSize);
        // check that the taskid of all TaskStatuses in statuses begins status.getId()
        assertThat(statuses).extracting(TaskStatus::getId).allMatch(taskId -> taskId.startsWith(status.getId()));

        // docs not in error are still written though
        txFeature.nextTransaction();
        doc.refresh();
        assertEquals("test foo", doc.getPropertyValue("cpx_complex/foo"));
    }
}
