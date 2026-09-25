/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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

import static com.hyland.openarch.async.tasks.executors.UpdateReadAclsBatchTaskProcessor.BATCH_UPDATE_READ_ACLS_TASK_NAME;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toSet;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_IS_VERSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYONE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ_VERSION;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.SYSTEM_USERNAME;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.UNSUPPORTED_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.KEY_ACE_GRANT;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.KEY_ACE_PERMISSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.KEY_ACE_PRINCIPAL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.KEY_ACE_STATUS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.hyland.openarch.ecm.core.api.SystemPrincipal;
import com.hyland.openarch.ecm.core.api.query.QueryParameters;
import com.hyland.openarch.ecm.core.query.sql.HXQLParser;
import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.ecm.core.storage.StateHelper;
import com.hyland.openarch.ecm.core.storage.dbs.DBSDocumentState;
import com.hyland.openarch.ecm.core.storage.dbs.DBSSession;
import com.hyland.openarch.ecm.core.storage.dbs.StateAccessor;
import com.hyland.openarch.runtime.api.Framework;

import com.hyland.openarch.async.tasks.TaskService;
import com.hyland.openarch.async.tasks.message.ScrollerTask;

/**
 * Denormalizer of sys_acl into sys_racl.
 */
public class ACLDenormalizer {

    private static final int ASYNC_THRESHOLD = 500;

    /** Keys used when computing Read ACLs. */
    private static final Set<String> READ_ACL_RECURSION_KEYS = Set.of(SYS_READ_ACL, SYS_ACL, SYSVER_IS_VERSION,
            SYS_PARENT_ID);

    private final SecurityService securityService;

    public ACLDenormalizer(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void updateDenormalizedProperties(String id, StateAccessor stateAccessor) {
        new ACLDenormalizerProcessor(stateAccessor).updateReadACLs(id);
    }

    public void updateDenormalizedPropertiesNoCache(Collection<String> ids, StateAccessor stateAccessor) {
        new ACLDenormalizerProcessor(stateAccessor).updateReadACLsNoCache(ids);
    }

    public void updateDenormalizedPropertiesWithDescendants(String id, StateAccessor stateAccessor,
            DBSSession session) {
        new ACLDenormalizerProcessor(stateAccessor).updateReadACLsWithDescendants(id, session);
    }

    private class ACLDenormalizerProcessor {

        private final StateAccessor stateAccessor;

        private final Set<String> readPermissions;

        public ACLDenormalizerProcessor(StateAccessor stateAccessor) {
            this.stateAccessor = stateAccessor;
            readPermissions = new HashSet<>(securityService.getAllPermissionsContaining(READ));
        }

        /**
         * Updates the Read ACLs on a document (not recursively).
         */
        public void updateReadACLs(String id) {
            DBSDocumentState docState = stateAccessor.getStateForUpdate(id);
            docState.put(SYS_READ_ACL, getReadACL(docState.getState()));
        }

        public void updateReadACLsNoCache(Collection<String> ids) {
            ids.forEach(this::updateReadACLsNoCache);
        }

        /**
         * Updates the Read ACLs on a document (not recursively), without polluting caches.
         * <p>
         * When fetching parents recursively to compute inheritance, the regular transient space and repository caching
         * are used.
         */
        private void updateReadACLsNoCache(String id) {
            // no transient for state read, and we don't want to trash caches
            // fetch from repository only the properties needed for Read ACL computation and recursion
            State state = stateAccessor.readPartialStateNoCache(id, READ_ACL_RECURSION_KEYS);
            StateDiff diff = getReadACLStateDiff(state);
            if (!diff.isEmpty()) {
                // no transient for state write, we write directly and just invalidate caches
                stateAccessor.updateStateNoCache(id, diff);
            }
        }

        private StateDiff getReadACLStateDiff(State state) {
            State oldState = new State(1);
            State newState = new State(1);
            oldState.put(SYS_READ_ACL, state.get(SYS_READ_ACL));
            newState.put(SYS_READ_ACL, getReadACL(state));
            return StateHelper.diff(oldState, newState);
        }

        /**
         * Updates the Read ACLs on a document recursively.
         */
        public void updateReadACLsWithDescendants(String id, DBSSession session) {
            // update the doc itself
            updateReadACLs(id);

            // check if we have a small enough number of descendants that we can process them synchronously
            int limit = ASYNC_THRESHOLD;
            Set<String> ids;
            try (Stream<State> states = session.getDescendants(id, Set.of(), limit)) {
                ids = states.map(state -> (String) state.get(SYS_ID)).collect(toSet());
            }
            if (limit == 0 || ids.size() < limit) {
                // small enough, update all descendants synchronously
                ids.forEach(this::updateReadACLs);
                return;
            }

            // update the direct children (with a limit in case it's too big)
            String nxql = "SELECT sys_id FROM SysContent WHERE sys_parentId = '%s'".formatted(id);
            QueryParameters queryParams = new QueryParameters(new SystemPrincipal(), limit, 0);
            var res = session.queryProjection(HXQLParser.parse(nxql), queryParams, false, 0);
            for (Map<String, Object> map : res) {
                String childId = (String) map.get(SYS_ID);
                updateReadACLs(childId);
            }

            // bulk async task to do the whole tree
            nxql = "SELECT sys_id FROM SysContent WHERE sys_ancestorIds = '%s'".formatted(id);
            TaskService asyncTasksService = Framework.getService(TaskService.class);
            int batchSize = 250;
            ScrollerTask command = new ScrollerTask.Builder(BATCH_UPDATE_READ_ACLS_TASK_NAME, nxql,
                    session.getRepositoryName(), SYSTEM_USERNAME).withBatchSize(batchSize).withTimestamp(null).build();
            asyncTasksService.submitTask(command);
        }

        /**
         * Gets the Read ACL (flat list of users having Read permission, including inheritance) on a document.
         */
        private List<String> getReadACL(State state) {
            Set<String> racls = new TreeSet<>(); // sorted, unique, to have canonical order
            boolean processReadVersion = false;
            LOOP: do {
                if (TRUE.equals(state.get(SYSVER_IS_VERSION))) {
                    processReadVersion = true;
                }
                @SuppressWarnings("unchecked")
                List<Object> aceList = (List<Object>) state.get(SYS_ACL);
                if (aceList != null) {
                    for (Object aceSer : aceList) {
                        State aceMap = (State) aceSer;
                        String principal = (String) aceMap.get(KEY_ACE_PRINCIPAL);
                        String permission = (String) aceMap.get(KEY_ACE_PERMISSION);
                        Boolean granted = (Boolean) aceMap.get(KEY_ACE_GRANT);
                        Long status = (Long) aceMap.get(KEY_ACE_STATUS);
                        if (processReadVersion && READ_VERSION.equals(permission)) {
                            permission = READ;
                        }
                        if (TRUE.equals(granted) && readPermissions.contains(permission)
                                && (status == null || status == 1)) {
                            racls.add(principal);
                        }
                        if (FALSE.equals(granted)) {
                            if (!EVERYONE.equals(principal)) {
                                // TODO log
                                racls.add(UNSUPPORTED_ACL);
                            }
                            break LOOP;
                        }
                    }
                }
                String parentId = (String) state.get(SYS_PARENT_ID);
                state = parentId == null ? null : stateAccessor.getStateForRead(parentId);
            } while (state != null);
            if (racls.isEmpty()) {
                return null;
            }
            return new ArrayList<>(racls);
        }
    }

}
