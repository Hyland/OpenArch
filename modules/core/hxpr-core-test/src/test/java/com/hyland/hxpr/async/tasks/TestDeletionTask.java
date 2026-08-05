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
import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ecm.core.async.tasks.DeleteBatchTaskProcessor.BATCH_DELETE_TASK_NAME;
import static org.nuxeo.ecm.core.test.DocumentSetRepositoryInit.CREATED_TOTAL;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.runtime.springboot.junit.RuntimeTestState;
import org.springframework.boot.test.context.SpringBootTest;

import com.hyland.hxpr.async.tasks.message.ScrollerTask;

@SpringBootTest(classes = RuntimeTestState.class)
public class TestDeletionTask extends AbstractTestAsyncTask {

    @Test
    public void testDeletionTask() {
        DocumentModel model = session.getDocument(new PathRef("/default-domain/workspaces/test"));
        String nxql = String.format("SELECT * from SysContent where sys_ancestorIds='%s'", model.getId());
        assertThat(session.query(nxql).size()).isEqualTo(CREATED_TOTAL);

        var taskCommand = new ScrollerTask.Builder(BATCH_DELETE_TASK_NAME, nxql, session.getRepositoryName(),
                session.getPrincipal().getId()).build();
        var taskId = asyncTasksService.submitTask(taskCommand);

        txFeature.nextTransaction();

        var status = asyncTasksService.getStatus(taskId);
        assertThat(status.getState()).isEqualTo(COMPLETED);
        assertThat(session.query(nxql)).hasSize(0);
    }

    @Test
    public void testDeletionTaskTriggered() {
        DocumentModel model = session.getDocument(new PathRef("/default-domain/workspaces/test"));
        session.removeDocument(model.getRef());
        txFeature.nextTransaction();

        String nxql = String.format("SELECT * from SysContent where sys_ancestorIds='%s'", model.getId());
        assertThat(session.query(nxql)).hasSize(0);
    }
}
