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
import static com.hyland.hxpr.async.tasks.DummyBatchTaskProcessor.DUMMY_TASK_NAME;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static org.nuxeo.ecm.core.schema.TypeConstants.SYS_FILE;

import com.hyland.hxpr.async.tasks.message.BatchTask;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import com.hyland.openarch.runtime.springboot.junit.RuntimeTestState;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.springframework.boot.test.context.SpringBootTest;

import com.hyland.hxpr.async.tasks.message.DocumentTask;
import com.hyland.hxpr.async.tasks.message.ScrollerTask;

/**
 * Test class for validating the AsyncTasksService methods without considering the parts tied to the implementation (AWS
 * or Memory).
 */
@SpringBootTest(classes = RuntimeTestState.class)
public class TestAsyncTasksService extends AbstractTestAsyncTasksService {

    @Test
    public void testEmptyQuery() throws InterruptedException {
        String nxql = "SELECT * FROM SysContent where sys_parentId='nonExistentId'";
        assertThat(session.query(nxql)).hasSize(0);

        String taskId = asyncTasksService.submitTask(new ScrollerTask.Builder(DUMMY_TASK_NAME, nxql,
                session.getRepositoryName(), session.getPrincipal().getId()).build());
        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(10)), "Bulk action didn't finish");

        TaskStatus status = asyncTasksService.getStatus(taskId);
        assertThat(status.getId()).isEqualTo(taskId);
        assertThat(status.getState()).isEqualTo(COMPLETED);
        assertThat(status.getProcessed()).isEqualTo(0);
        assertThat(status.hasError()).isFalse();
    }

    @Test
    public void testNullQuery() {
        try {
            asyncTasksService.submitTask(
                    new ScrollerTask.Builder(DUMMY_TASK_NAME, null, session.getRepositoryName(), "user").build());
            fail("null query should raise error");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testInvalidQuery() throws InterruptedException {
        String nxql = "DROP DATABASE is not a valid NXQL command";
        submitAndAssertAnInvalidNXQLQuery(nxql);
    }

    @Test
    public void testQueryWithError() throws InterruptedException {
        var nxql = "SELECT * FROM SysContent WHERE sys_path = 'non/existing/path'";
        submitAndAssertAnInvalidNXQLQuery(nxql);
    }

    @Test
    public void testUnExistingType() throws InterruptedException {
        var nxql = "SELECT * FROM Image";
        submitAndAssertAnInvalidNXQLQuery(nxql);
    }

    @Test
    public void testInvalidInOperator() throws InterruptedException {
        var nxql = "SELECT * FROM SysContent WHERE ecm:primaryType IN (1, 2, 3)";
        submitAndAssertAnInvalidNXQLQuery(nxql);
    }

    protected void submitAndAssertAnInvalidNXQLQuery(String nxql) throws InterruptedException {
        String taskId = asyncTasksService.submitTask(
                new ScrollerTask.Builder(DUMMY_TASK_NAME, nxql, session.getRepositoryName(), "user").build());
        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(10)), "Async task didn't finish");
        TaskStatus status = asyncTasksService.getStatus(taskId);
        assertThat(status.getId()).isEqualTo(taskId);
        assertThat(status.getState()).isEqualTo(COMPLETED);
        assertThat(status.getProcessed()).isEqualTo(0);
        assertThat(status.hasError()).isTrue();
        assertThat(status.getErrorCode()).isEqualTo(SC_BAD_REQUEST);
    }

    @Test
    public void testInvalidAction() {
        String nxql = "SELECT * FROM SysContent";
        try {
            asyncTasksService.submitTask(
                    new ScrollerTask.Builder("unknownAction", nxql, session.getRepositoryName(), "user").build());
            fail("unknown action should raise error");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testNullUser() {
        // null user
        try {
            asyncTasksService.submitTask(
                    new BatchTask.Builder(DUMMY_TASK_NAME, session.getRepositoryName(), null, 10).build());
            fail("null username should raise error");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testInvalidOptionalParam() {
        try {
            asyncTasksService.submitTask(new DocumentTask(DUMMY_TASK_NAME, "UnknownRepo", "user", null));
            fail("unknown repo should raise error");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    @Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/async-task-failure-contrib.xml")
    public void testPolicyOnFailure() throws Exception {
        // add a doc
        DocumentModel doc = session.createDocumentModel("/", "doc", SYS_FILE);
        doc.create();
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        // run an action that fails with a continue on failure option
        String commandId = asyncTasksService.submitTask(
                new DocumentTask(FailTaskProcessor.FAIL_TASK_NAME, session.getRepositoryName(), TEST_ADMIN_USER, null));
        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(60)), "Async task didn't finish");

        TaskStatus status = asyncTasksService.getStatus(commandId);
        assertNotNull(status);
        assertEquals(COMPLETED, status.getState());
        assertTrue(status.hasError());
    }
}
