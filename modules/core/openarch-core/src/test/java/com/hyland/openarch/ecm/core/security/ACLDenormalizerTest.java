/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.KEY_ACE_GRANT;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.KEY_ACE_PERMISSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.KEY_ACE_PRINCIPAL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.KEY_ACE_STATUS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYONE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.UNSUPPORTED_ACL;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.ecm.core.storage.dbs.DBSDocumentState;
import com.hyland.openarch.ecm.core.storage.dbs.StateAccessor;

class ACLDenormalizerTest {

    private static State grantAce(String principal, String permission) {
        State ace = new State();
        ace.put(KEY_ACE_PRINCIPAL, principal);
        ace.put(KEY_ACE_PERMISSION, permission);
        ace.put(KEY_ACE_GRANT, Boolean.TRUE);
        ace.put(KEY_ACE_STATUS, 1L);
        return ace;
    }

    @Test
    void shouldWriteReadAclDiffWhenAclGrantsRead() {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.getAllPermissionsContaining(READ)).thenReturn(Set.of(READ));
        StateAccessor stateAccessor = mock(StateAccessor.class);
        State state = new State();
        state.put(SYS_ACL, List.of(grantAce(EVERYONE, READ)));
        when(stateAccessor.readPartialStateNoCache(eq("doc1"), any())).thenReturn(state);

        new ACLDenormalizer(securityService).updateDenormalizedPropertiesNoCache(List.of("doc1"), stateAccessor);

        verify(stateAccessor).updateStateNoCache(eq("doc1"), any(StateDiff.class));
    }

    @Test
    void shouldNotWriteWhenReadAclUnchanged() {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.getAllPermissionsContaining(READ)).thenReturn(Set.of(READ));
        StateAccessor stateAccessor = mock(StateAccessor.class);
        // no ACL -> the computed Read ACL is null, matching the (absent) stored value, so the diff is empty
        when(stateAccessor.readPartialStateNoCache(eq("doc1"), any())).thenReturn(new State());

        new ACLDenormalizer(securityService).updateDenormalizedPropertiesNoCache(List.of("doc1"), stateAccessor);

        verify(stateAccessor, never()).updateStateNoCache(any(), any());
    }

    private static State ace(String principal, String permission, Boolean grant) {
        State ace = new State();
        ace.put(KEY_ACE_PRINCIPAL, principal);
        ace.put(KEY_ACE_PERMISSION, permission);
        ace.put(KEY_ACE_GRANT, grant);
        return ace;
    }

    private static SecurityService readSecurityService() {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.getAllPermissionsContaining(READ)).thenReturn(Set.of(READ));
        return securityService;
    }

    @Test
    void grantedReadProducesReadAcl() {
        StateAccessor accessor = mock(StateAccessor.class);
        DBSDocumentState docState = mock(DBSDocumentState.class);
        State state = new State();
        state.put(SYS_ACL, List.of(ace("alice", READ, TRUE)));
        when(docState.getState()).thenReturn(state);
        when(accessor.getStateForUpdate("id")).thenReturn(docState);

        new ACLDenormalizer(readSecurityService()).updateDenormalizedProperties("id", accessor);

        verify(docState).put(SYS_READ_ACL, List.of("alice"));
    }

    @Test
    void denyEveryoneStopsInheritanceButKeepsGrants() {
        StateAccessor accessor = mock(StateAccessor.class);
        DBSDocumentState docState = mock(DBSDocumentState.class);
        State state = new State();
        state.put(SYS_ACL, List.of(ace("alice", READ, TRUE), ace(EVERYONE, READ, FALSE)));
        state.put(SYS_PARENT_ID, "parent"); // recursion must stop at the EVERYONE deny
        when(docState.getState()).thenReturn(state);
        when(accessor.getStateForUpdate("id")).thenReturn(docState);

        new ACLDenormalizer(readSecurityService()).updateDenormalizedProperties("id", accessor);

        verify(docState).put(SYS_READ_ACL, List.of("alice"));
        verify(accessor, never()).getStateForRead(any());
    }

    @Test
    void denyNonEveryoneMarksUnsupported() {
        StateAccessor accessor = mock(StateAccessor.class);
        DBSDocumentState docState = mock(DBSDocumentState.class);
        State state = new State();
        state.put(SYS_ACL, List.of(ace("bob", READ, FALSE)));
        when(docState.getState()).thenReturn(state);
        when(accessor.getStateForUpdate("id")).thenReturn(docState);

        new ACLDenormalizer(readSecurityService()).updateDenormalizedProperties("id", accessor);

        verify(docState).put(SYS_READ_ACL, List.of(UNSUPPORTED_ACL));
    }

    @Test
    void emptyAclReturnsNull() {
        StateAccessor accessor = mock(StateAccessor.class);
        DBSDocumentState docState = mock(DBSDocumentState.class);
        when(docState.getState()).thenReturn(new State());
        when(accessor.getStateForUpdate("id")).thenReturn(docState);

        new ACLDenormalizer(readSecurityService()).updateDenormalizedProperties("id", accessor);

        verify(docState).put(eq(SYS_READ_ACL), isNull());
    }

    @Test
    void inheritsReadAclFromParent() {
        StateAccessor accessor = mock(StateAccessor.class);
        DBSDocumentState docState = mock(DBSDocumentState.class);
        State child = new State();
        child.put(SYS_PARENT_ID, "parent");
        State parent = new State();
        parent.put(SYS_ACL, List.of(ace("carol", READ, TRUE)));
        when(docState.getState()).thenReturn(child);
        when(accessor.getStateForUpdate("id")).thenReturn(docState);
        when(accessor.getStateForRead("parent")).thenReturn(parent);

        new ACLDenormalizer(readSecurityService()).updateDenormalizedProperties("id", accessor);

        verify(docState).put(SYS_READ_ACL, List.of("carol"));
    }
}
