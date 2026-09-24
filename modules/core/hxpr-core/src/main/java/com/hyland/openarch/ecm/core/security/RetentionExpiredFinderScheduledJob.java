/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.security;

import static com.hyland.openarch.ecm.core.async.tasks.RetentionExpiredBatchTaskProcessor.BATCH_RETENTION_EXPIRED_TASK_NAME;

import java.time.Instant;

import com.hyland.openarch.ecm.core.api.security.SecurityConstants;
import com.hyland.openarch.ecm.core.repository.RepositoryService;
import com.hyland.openarch.ecm.core.scheduler.ScheduledJob;
import com.hyland.openarch.runtime.api.Framework;

import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.message.ScrollerTask;

/**
 * Listener triggering the check of expired retentions.
 *
 * @since 11.1
 */
public class RetentionExpiredFinderScheduledJob implements ScheduledJob {

    public static final String QUERY = "SELECT * FROM SysContent, SysRelation"
            + " WHERE sysgov_retainUntil < TIMESTAMP '%s'";

    @Override
    public void run() {
        TaskService service = Framework.getService(TaskService.class);
        RepositoryService repositoryService = Framework.getService(RepositoryService.class);

        Instant now = Instant.now();
        String nxql = String.format(QUERY, now);

        for (String repositoryName : repositoryService.getRepositoryNames()) {
            // Force the timestamp to be null as it is not related to a cache invalidation
            ScrollerTask task = new ScrollerTask.Builder(BATCH_RETENTION_EXPIRED_TASK_NAME, nxql, repositoryName,
                    SecurityConstants.SYSTEM_USERNAME).withTimestamp(null).build();
            service.submitTask(task);
        }
    }

}
