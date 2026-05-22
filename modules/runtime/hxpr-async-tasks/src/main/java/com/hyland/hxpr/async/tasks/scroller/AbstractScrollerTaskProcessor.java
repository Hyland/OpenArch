/*
 * (C) Copyright 2025 Hyland (https://www.hyland.com/) and others.
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
package com.hyland.hxpr.async.tasks.scroller;

import static com.hyland.hxpr.async.tasks.TaskStatus.State.COMPLETED;
import static com.hyland.hxpr.async.tasks.TaskStatus.State.SCROLLING_RUNNING;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import java.time.Duration;
import java.util.List;

import javax.security.auth.login.LoginException;

import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.hxpr.async.tasks.TaskProcessor;
import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.message.ScrollerTask;

public abstract class AbstractScrollerTaskProcessor implements TaskProcessor<ScrollerTask> {

    private static final Logger log = LoggerFactory.getLogger(AbstractScrollerTaskProcessor.class);

    // a new transaction is used for every batch, so this doesn't need to be huge
    private static final int TRANSACTION_TIMEOUT = (int) Duration.ofHours(1).toSeconds();

    private static final int SCROLL_KEEPALIVE = (int) Duration.ofMinutes(5).toSeconds();

    @Override
    public Class<ScrollerTask> getTaskClass() {
        return ScrollerTask.class;
    }

    @Override
    public void process(ScrollerTask task) {
        // update bulk task status
        var asyncTasksService = Framework.getService(TaskService.class);
        var status = asyncTasksService.getStatus(task.getTaskId());
        status.setState(SCROLLING_RUNNING);
        asyncTasksService.setStatus(status);

        TransactionHelper.runInTransaction(TRANSACTION_TIMEOUT, () -> {
            int documentCount = 0;
            int bucketCount = 0;
            String username = task.getUsername();
            try (var __ = Framework.loginUserOrSystem(username)) {
                var session = CoreInstance.getCoreSessionSystem(task.getRepository());
                var result = session.scroll(task.getQuery(), task.getBatchSize(), SCROLL_KEEPALIVE);
                while (result.hasResults()) {
                    List<String> documentIds = result.getResults();
                    documentCount += documentIds.size();
                    bucketCount += 1;
                    process(task, documentIds, bucketCount);
                    // new transaction
                    TransactionHelper.commitOrRollbackTransaction();
                    TransactionHelper.startTransaction(TRANSACTION_TIMEOUT);
                    result = session.scroll(result.getScrollId());
                }
                // handle post-processing
                handlePostProcessing(task);
                status.setState(COMPLETED);
                if (documentCount == 0) {
                    log.debug("The task {} is completed with no documents ({})", task.getTaskId(), task.getBulkTaskName());
                } else {
                    log.info("The task {} is completed, submitted {} batches with {} documents ({})", task.getTaskId(), bucketCount, documentCount, task.getBulkTaskName());
                }
            } catch (LoginException | IllegalArgumentException | QueryParseException | DocumentNotFoundException e) {
                String message = e instanceof LoginException ? "Invalid user: %s".formatted(username)
                        : "Invalid query: %s".formatted(task);
                log.error(message, e);
                status.setState(COMPLETED);
                status.setErrorCode(SC_BAD_REQUEST);
                status.setErrorMessage("Invalid user or query");
            } finally {
                // handle final processing
                handleFinalProcessing(task, documentCount, bucketCount);
                asyncTasksService.setStatus(status);
            }
        });
    }

    /**
     * Process a batch of document IDs from the scroll result.
     *
     * @param batchNumber the 1-based batch sequence number within this scroll operation
     */
    protected abstract void process(ScrollerTask task, List<String> documentIds, int batchNumber);

    /**
     * Handle any post-processing after all batches are processed
     */
    protected void handlePostProcessing(ScrollerTask task) {
        // nothing by default
    }

    /**
     * Handle final processing, even if any errors occurred
     */
    protected void handleFinalProcessing(ScrollerTask task, int documentCount, int bucketCount) {
        // nothing by default
    }

}
