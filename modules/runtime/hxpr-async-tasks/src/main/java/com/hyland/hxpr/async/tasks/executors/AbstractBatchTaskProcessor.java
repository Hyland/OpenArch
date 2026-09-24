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
package com.hyland.hxpr.async.tasks.executors;

import static com.hyland.hxpr.async.tasks.TaskStatus.State.COMPLETED;
import static com.hyland.hxpr.async.tasks.TaskStatus.State.UNKNOWN;
import static java.util.Objects.requireNonNullElse;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import javax.security.auth.login.LoginException;

import org.apache.commons.collections4.ListUtils;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.query.sql.HXQL;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.hxpr.async.tasks.TaskProcessor;
import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.message.BatchTask;

public abstract class AbstractBatchTaskProcessor implements TaskProcessor<BatchTask> {

    private static final Logger log = LoggerFactory.getLogger(AbstractBatchTaskProcessor.class);

    private static final String SELECT_START = "SELECT * FROM SysContent, SysRelation WHERE sys_id IN (";

    private static final String SELECT_SEP = ", ";

    private static final String SELECT_END = ")";

    @Override
    public Class<BatchTask> getTaskClass() {
        return BatchTask.class;
    }

    @Override
    public void process(BatchTask task) {
        // Get initial scroller task
        int timeout = (int) requireNonNullElse(task.getBatchTransactionTimeoutDuration(), Duration.ZERO).toSeconds();
        var size = task.getDocumentIds().size();
        for (List<String> batch : ListUtils.partition(task.getDocumentIds(), task.getBatchSize())) {
            TransactionHelper.runInTransaction(timeout, () -> {
                String username = task.getUsername();
                String repository = task.getRepository();
                try (var __ = Framework.loginUserOrSystem(username)) {
                    CoreSession session = repository == null ? null : CoreInstance.getCoreSession(repository);
                    process(task, batch, session);
                } catch (LoginException e) {
                    throw new NuxeoException(e);
                }
            });
        }
        handlePostProcessing(task);
        // Update the status of the task
        var asyncTasksService = Framework.getService(TaskService.class);
        var status = asyncTasksService.getStatus(task.getTaskId());
        if (status.getState() == UNKNOWN) {
            log.error("Missing status for batch task: {}", task.getTaskId());
        }
        status.setState(COMPLETED);
        status.addProcessed(size);
        log.info("Batch task completed: {} ({})", task.getTaskId(), task.getTaskName());
        asyncTasksService.setStatus(status);
    }

    protected List<DocumentModel> loadDocuments(CoreSession session, List<String> documentIds) {
        String query = documentIds.stream()
                                  .map(HXQL::escapeString)
                                  .collect(Collectors.joining(SELECT_SEP, SELECT_START, SELECT_END))
                                  .toString();
        // read invalidation
        session.save();
        return session.query(query);
    }

    protected abstract void process(BatchTask task, List<String> documentIds, CoreSession session);

    protected void handlePostProcessing(BatchTask task) {
        // default does nothing
    }

}
