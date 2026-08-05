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
package org.nuxeo.ecm.core.async.tasks;

import static com.hyland.hxpr.async.tasks.TaskStatus.State.COMPLETED;
import static java.util.Objects.requireNonNullElse;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hyland.hxpr.async.tasks.message.RepositoryTask;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.nuxeo.runtime.transaction.TransactionRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyland.hxpr.async.tasks.TaskProcessor;
import com.hyland.hxpr.async.tasks.TaskService;

public class UpdateACEStatusTaskProcessor implements TaskProcessor<RepositoryTask> {

    private static final Logger log = LoggerFactory.getLogger(UpdateACEStatusTaskProcessor.class);

    public static final String UPDATE_ACE_STATUS_TASK_NAME = "updateAceStatus";

    public static final String TIMEOUT_PROPERTY = "timeout";

    public static final int DEFAULT_BATCH_SIZE = 20;

    public static final String QUERY = "SELECT sys_id, sys_acl/*1/principal, sys_acl/*1/permission,"
            + " sys_acl/*1/grant, sys_acl/*1/creator, sys_acl/*1/begin, sys_acl/*1/end FROM SysContent"
            + " WHERE (sys_acl/*1/status = 0 AND sys_acl/*1/begin <= TIMESTAMP '%s')"
            + " OR (sys_acl/*1/status = 1 AND sys_acl/*1/end <= TIMESTAMP '%s')";

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    protected int batchSize = DEFAULT_BATCH_SIZE;

    @Override
    public String getName() {
        return UPDATE_ACE_STATUS_TASK_NAME;
    }

    @Override
    public Class<RepositoryTask> getTaskClass() {
        return RepositoryTask.class;
    }

    @Override
    public void process(RepositoryTask task) {
        Duration batchTimeout = Duration.ofSeconds(
                requireNonNullElse((Long) task.getProperties().get(TIMEOUT_PROPERTY), 0L));
        int timeout = (int) requireNonNullElse(batchTimeout, Duration.ZERO).toSeconds();
        var asyncTasksService = Framework.getService(TaskService.class);
        var status = asyncTasksService.getStatus(task.getTaskId());

        TransactionHelper.startTransaction();
        var ok = false;
        try {
            String username = task.getUsername();
            String repository = task.getRepository();
            try (var __ = Framework.loginUserOrSystem(username)) {
                CoreSession session = repository == null ? null : CoreInstance.getCoreSession(repository);
                if (session != null) {
                    var updatedACECount = executeTask(session);
                    status.addProcessed(updatedACECount);
                }
            }
            // Update the status of the indexing task
            status.setState(COMPLETED);
            asyncTasksService.setStatus(status);
        } catch (Exception e) {
            throw new NuxeoException(e);
        } finally {
            if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
                if (!ok) {
                    log.trace("{} is suspending, rollbacking", task.getTaskId());
                    TransactionHelper.setTransactionRollbackOnly();
                }
                TransactionHelper.commitOrRollbackTransaction();
            }
        }

    }

    private int executeTask(CoreSession session) {
        String formattedDate = FORMATTER.format(ZonedDateTime.now());

        IterableQueryResult result = session.queryAndFetch(String.format(QUERY, formattedDate, formattedDate),
                NXQL.NXQL);
        Map<String, List<ACE>> docIdsToACEs = new HashMap<>();
        try {
            for (Map<String, Object> map : result) {
                String docId = (String) map.get(SYS_ID);
                List<ACE> aces = docIdsToACEs.computeIfAbsent(docId, k -> new ArrayList<>());
                String principal = (String) map.get("sys_acl/*1/principal");
                String permission = (String) map.get("sys_acl/*1/permission");
                Boolean grant = (Boolean) map.get("sys_acl/*1/grant");
                String creator = (String) map.get("sys_acl/*1/creator");
                Instant begin = (Instant) map.get("sys_acl/*1/begin");
                Instant end = (Instant) map.get("sys_acl/*1/end");
                ACE ace = ACE.builder()
                             .principal(principal)
                             .permission(permission)
                             .isGranted(grant)
                             .creator(creator)
                             .begin(begin)
                             .end(end)
                             .build();
                aces.add(ace);
            }
        } finally {
            result.close();
        }

        int aclUpdatedCount = 0;
        for (Map.Entry<String, List<ACE>> entry : docIdsToACEs.entrySet()) {
            try {
                DocumentRef ref = new IdRef(entry.getKey());
                ACL acl = session.getDocument(ref).getLocalACL();
                // re-set the ACL to actually write the new status
                session.setACL(ref, acl, true);
                session.save();
                aclUpdatedCount++;
                if (aclUpdatedCount % batchSize == 0) {
                    if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
                        TransactionHelper.commitOrRollbackTransaction();
                    }
                    TransactionHelper.startTransaction();
                }
            } catch (TransactionRuntimeException e) {
                if (e.getMessage().contains("Transaction timeout")) {
                    batchSize = 1;
                }
                throw e;
            }
        }
        return aclUpdatedCount;
    }

}
