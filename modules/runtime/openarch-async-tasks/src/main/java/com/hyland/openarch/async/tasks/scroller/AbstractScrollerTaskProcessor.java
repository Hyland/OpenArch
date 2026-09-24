/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.async.tasks.scroller;

import static com.hyland.openarch.async.tasks.TaskStatus.State.COMPLETED;
import static com.hyland.openarch.async.tasks.TaskStatus.State.SCROLLING_RUNNING;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import java.time.Duration;
import java.util.List;

import javax.security.auth.login.LoginException;

import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.DocumentNotFoundException;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.openarch.async.tasks.TaskProcessor;
import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.async.tasks.message.ScrollerTask;

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
                // Persist the terminal state before any additional work (e.g. logging) that
                // could be interrupted, so a mid-shutdown pod can't leave the task stuck
                // in a non-terminal state forever.
                asyncTasksService.setStatus(status);
                handleFinalProcessing(task, documentCount, bucketCount);
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
     * Handle final processing, even if any errors occurred.
     * <p>
     * On successful completion (or on a query/login error, which is treated as completed with
     * an error code) this runs after the terminal {@code COMPLETED} status has already been
     * persisted, so an unclean interruption (e.g. pod kill) partway through it leaves the task
     * reporting success with no further retry or redelivery. On any other exception the status
     * persisted just before this call may still be non-terminal ({@code SCROLLING_RUNNING}); the
     * caller is responsible for eventually forcing the task to {@code ABORTED} once the exception
     * propagates out of {@link #process(ScrollerTask)}.
     * <p>
     * Implementations must therefore be side-effect-free (e.g. logging/metrics only), or
     * otherwise safe to skip or run partially. Any side effect that must survive a crash should
     * be triggered from {@link #handlePostProcessing(ScrollerTask)} instead — that runs before the
     * terminal status is set, not from here, since a side effect started here has no persisted
     * status change to protect it if the pod dies before it finishes.
     */
    protected void handleFinalProcessing(ScrollerTask task, int documentCount, int bucketCount) {
        // nothing by default
    }

}
