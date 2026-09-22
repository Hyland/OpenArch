/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.ecm.core.security;

import static org.nuxeo.ecm.core.async.tasks.UpdateACEStatusTaskProcessor.UPDATE_ACE_STATUS_TASK_NAME;
import static com.hyland.openarch.runtime.api.login.LoginService.SYSTEM_USERNAME;

import com.hyland.hxpr.async.tasks.TaskService;
import com.hyland.hxpr.async.tasks.message.RepositoryTask;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.scheduler.ScheduledJob;
import com.hyland.openarch.runtime.api.Framework;

/**
 * Listener triggering the work updating ACE status.
 *
 * @since 7.4
 */
public class UpdateACEStatusScheduledJob implements ScheduledJob {

    @Override
    public void run() {
        TaskService taskService = Framework.getService(TaskService.class);
        Framework.getService(RepositoryManager.class).getRepositoryNames().forEach(repoId -> {
            // Force the timestamp to null as it is not related to a cache invalidation
            taskService.submitTask(new RepositoryTask(UPDATE_ACE_STATUS_TASK_NAME, repoId, SYSTEM_USERNAME));
        });
    }

}
