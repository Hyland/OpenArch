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
package com.hyland.openarch.async.tasks;

import static com.hyland.openarch.async.tasks.TaskStatus.State.COMPLETED;
import static com.hyland.openarch.async.tasks.DummyBatchTaskProcessor.DUMMY_TASK_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.hyland.openarch.async.tasks.message.ScrollerTask;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

@Features(CoreFeature.class)
@Deploy("com.hyland.openarch.async.tasks")
@Deploy("com.hyland.openarch.async.tasks.tests:OSGI-INF/test-async-tasks-contrib.xml")
public class AbstractTestAsyncTasksService extends RuntimeSpringBootTest {

    @RuntimeInject
    protected CoreSession session;

    @RuntimeInject
    protected TaskService asyncTasksService;

    protected List<String> docIds = new ArrayList<>();

    protected String folderDocId;

    protected void waitForAsyncCompletion() throws InterruptedException {
        nextTransaction();
        asyncTasksService.awaitCompletion(Duration.of(10, TimeUnit.SECONDS.toChronoUnit()));
    }

    protected void nextTransaction() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    @BeforeEach
    public void setup() {
        // Create several documents
        var folder = session.createDocumentModel("/", "FolderTest", "SysFolder");
        folder.create();
        folderDocId = folder.getId();
        var doc1 = session.createDocumentModel(folder.getPathAsString(), "Document1", "SysFile");
        doc1.create();
        docIds.add(doc1.getId());
        var doc2 = session.createDocumentModel(folder.getPathAsString(), "Document2", "SysFile");
        doc2.create();
        docIds.add(doc2.getId());
        session.save();
        nextTransaction();
    }

    @AfterEach
    public void cleanUp() {
        docIds.forEach(id -> session.removeDocument(new IdRef(id)));
        if (StringUtils.isNotBlank(folderDocId)) {
            session.removeDocument(new IdRef(folderDocId));
        }
    }

    @Test
    public void serviceCanTriggerAsyncTask() throws InterruptedException {
        var taskCommand = new ScrollerTask.Builder(DUMMY_TASK_NAME, "SELECT * FROM SysFile", session.getRepositoryName(),
                session.getPrincipal().getId()).build();
        var taskId = asyncTasksService.submitTask(taskCommand);

        waitForAsyncCompletion();

        // Check that the documents have been updated
        docIds.forEach(id -> {
            var doc = session.getDocument(new IdRef(id));
            assertThat(doc.getPropertyValue(SYS_DESCRIPTION)).isEqualTo("Updated description by dummy taskName");
        });
        assertThat(asyncTasksService.getStatus(taskId).getState()).isEqualTo(COMPLETED);
    }
}
