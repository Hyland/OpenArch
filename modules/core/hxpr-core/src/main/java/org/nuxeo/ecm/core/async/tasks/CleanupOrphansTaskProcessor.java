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
package org.nuxeo.ecm.core.async.tasks;

import static com.hyland.hxpr.async.tasks.TaskStatus.State.COMPLETED;
import static java.util.Objects.requireNonNullElse;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;

import com.hyland.hxpr.async.tasks.TaskProcessor;
import com.hyland.hxpr.async.tasks.TaskService;
import org.nuxeo.ecm.core.SessionImpl;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CleanupOrphansTaskProcessor implements TaskProcessor<CleanupOrphansTask> {

    public static final String CLEANUP_ORPHANS_TASK_NAME = "cleanupOrphans";

    public static final String SEARCH_QUERY = "SELECT sys_id, sys_parentId FROM SysContent WHERE sys_isTrashed = FALSE AND sysver_isVersion = FALSE";

    @Override
    public String getName() {
        return CLEANUP_ORPHANS_TASK_NAME;
    }

    @Override
    public Class<CleanupOrphansTask> getTaskClass() {
        return CleanupOrphansTask.class;
    }

    @Override
    public void process(CleanupOrphansTask task) {
        int timeout = (int) requireNonNullElse(task.getBatchTransactionTimeoutDuration(), Duration.ZERO).toSeconds();
        TransactionHelper.runInTransaction(timeout, () -> {
            try (var __ = Framework.loginSystem()) {
                CoreSession sysSession = CoreInstance.getCoreSessionSystem(task.getRepository());
                try (var result = sysSession.queryAndFetch(SEARCH_QUERY)) {
                    batchRemoveDocuments(sysSession, result, task);
                }
            }
        });
    }

    protected void batchRemoveDocuments(CoreSession session, Iterable<Map<String, Object>> results, CleanupOrphansTask task) {
        var asyncTasksService = Framework.getService(TaskService.class);
        var status = asyncTasksService.getStatus(task.getTaskId());
        String rootDocumentId = session.getRootDocument().getId();
        List<String> ids = new ArrayList<>();
        results.forEach(result -> {
            String id = (String) result.get(SYS_ID);
            if (!id.equals(rootDocumentId)) {
                if (!session.exists(new IdRef((String) result.get(SYS_PARENT_ID)))) {
                    ids.add(id);
                }
                if (ids.size() >= task.getBatchSize()) {
                    batchRemoveDocuments(session, ids);
                    ids.clear();
                }
            }
        });
        if (!ids.isEmpty()) {
            batchRemoveDocuments(session, ids);
        }
        session.save();
        status.setState(COMPLETED);
        asyncTasksService.setStatus(status);
    }

    protected void batchRemoveDocuments(CoreSession session, List<String> ids) {
        var internalSession = ((SessionImpl) session).getSession();
        for (String id : ids) {
            try {
                internalSession.removeDocument(id, session.getPrincipal());
            } catch (DocumentNotFoundException e) {
                // Document is already deleted
            }
        }
    }
}
