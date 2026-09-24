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
import static com.hyland.openarch.ecm.core.event.async.TrashBatchTaskProcessor.BATCH_TRASH_TASK_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.trash.TrashService.DOCUMENT_TRASHED;
import static com.hyland.openarch.ecm.core.test.DocumentSetRepositoryInit.CREATED_TOTAL;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.event.test.CapturingEventListener;
import com.hyland.openarch.runtime.springboot.junit.RuntimeTestState;
import org.springframework.boot.test.context.SpringBootTest;
import com.hyland.openarch.ecm.core.event.async.TrashBatchTaskProcessor;
import com.hyland.hxpr.async.tasks.message.ScrollerTask;

@SpringBootTest(classes = RuntimeTestState.class)
public class TestTrashTask extends AbstractTestAsyncTask {

    @Test
    public void testTrashTask() throws Exception {
        DocumentModel model = session.getDocument(new PathRef("/default-domain/workspaces/test"));
        String nxql = String.format("SELECT * from SysContent where sys_ancestorIds='%s'", model.getId());

        try (CapturingEventListener listener = new CapturingEventListener(DOCUMENT_TRASHED)) {
            String commandId = asyncTasksService.submitTask(
                    new ScrollerTask.Builder(BATCH_TRASH_TASK_NAME, nxql, session.getRepositoryName(),
                            session.getPrincipal().getId()).param(TrashBatchTaskProcessor.PARAM_NAME, Boolean.TRUE).build());
            assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(600)), "Bulk action didn't finish");

            var status = asyncTasksService.getStatus(commandId);
            assertNotNull(status);
            assertEquals(COMPLETED, status.getState());
            assertEquals(CREATED_TOTAL, listener.getCapturedEventCount(DOCUMENT_TRASHED));
        }

        txFeature.nextTransaction();

        session.query(nxql).forEach(doc -> assertTrue(doc.isTrashed(), "Doc '" + doc.getPath() + "' is not trashed"));
    }
}
