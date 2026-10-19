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
package com.hyland.openarch.ecm.core.storage.dbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.transaction.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import com.hyland.openarch.ecm.core.schema.FulltextConfiguration;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.security.SecurityPolicyService;
import com.hyland.openarch.ecm.core.security.SecurityService;
import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.runtime.mockito.MockitoFeature;
import com.hyland.openarch.runtime.test.runner.Features;

import com.hyland.openarch.async.tasks.message.DocumentTask;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

@ExtendWith(FeaturesExtension.class)
@Features({CoreFeature.class, MockitoFeature.class})
public class TestDBSTransactionState {

    private static final String REPO_ID = "test";

    private DBSTransactionState state;

    @Mock
    private SecurityService securityService;

    @Mock
    private SecurityPolicyService securityPolicyService;

    @Mock
    private DBSSession session;

    @Mock
    private DBSRepository repository;

    @Mock
    private DBSConnection connection;

    @BeforeEach
    public void setUp() {
        when(securityService.getSecurityPolicyService(REPO_ID)).thenReturn(securityPolicyService);
        doReturn(REPO_ID).when(repository).getName();
        doReturn(connection).when(repository).getConnection();
        FulltextConfiguration mockFulltextConfiguration = mock(FulltextConfiguration.class);
        SchemaManager schemaManager = mock(SchemaManager.class);
        when(connection.getSchemaManager()).thenReturn(schemaManager);
        when(schemaManager.getFulltextConfiguration()).thenReturn(mockFulltextConfiguration);
        when(mockFulltextConfiguration.isFulltextIndexable(any())).thenReturn(true);
        state = spy(new DBSTransactionState(repository, session));
    }

    @Test
    public void getFulltextTasksMustReturnTasksWithoutTimestampForCreatedDocuments() {
        DBSDocumentState doc = mock(DBSDocumentState.class);
        DBSDocumentState doc2 = mock(DBSDocumentState.class);
        doReturn("doc1").when(doc).getId();
        doReturn("doc2").when(doc2).getId();
        state.transientStates.put(doc.getId(), doc);
        state.transientStates.put(doc2.getId(), doc2);
        state.transientCreated.add(doc.getId());
        state.transientCreated.add(doc2.getId());

        doReturn(List.of(SYSFILE_BLOB)).when(state).getDirtyBinariesPaths(doc, true);
        doReturn(List.of(SYSFILE_BLOB)).when(state).getDirtyBinariesPaths(doc2, true);

        var tasks = state.getFulltextTasks();
        tasks.forEach(task -> assertNull(task.getTimestamp()));
    }

    @Test
    public void getFulltextTasksMustReturnTasksWithTimestampForUpdatedDocuments() {
        DBSDocumentState doc = mock(DBSDocumentState.class);
        DBSDocumentState doc2 = mock(DBSDocumentState.class);
        doReturn("doc1").when(doc).getId();
        doReturn("doc2").when(doc2).getId();
        state.transientStates.put(doc.getId(), doc);
        state.transientStates.put(doc2.getId(), doc2);

        doReturn(List.of(SYSFILE_BLOB)).when(state).getDirtyBinariesPaths(doc, true);
        doReturn(List.of(SYSFILE_BLOB)).when(state).getDirtyBinariesPaths(doc2, true);

        var tasks = state.getFulltextTasks();
        tasks.forEach(task -> assertNotNull(task.getTimestamp()));
    }

    @Test
    public void dirtyFulltextBinariesUseConnectionSchemaSnapshot() {
        var configuration = connection.getSchemaManager().getFulltextConfiguration();
        when(configuration.propPathBlobProperties()).thenReturn(Map.of("MyType", Set.of("myblob/key")));
        DBSDocumentState doc = mock(DBSDocumentState.class);
        when(doc.getPrimaryType()).thenReturn("MyType");
        doReturn(List.of("myblob/key", "other/key")).when(state).getDirtyPaths(doc);

        assertThat(state.getDirtyBinariesPaths(doc, true)).containsExactly("myblob/key");
        verify(repository, never()).getFulltextConfiguration();
    }

    @Test
    public void condenseDirtyPathsCondensesNewList() {
        // Given multiple list items modified (more than one index)
        List<String> paths = List.of("vector/0", "vector/1", "vector/2");

        // When condensing the paths
        List<String> result = state.condenseDirtyPaths(paths);

        // Then paths should be condensed to wildcard syntax
        assertThat(result).containsExactly("vector/*");
    }

    @Test
    public void condenseDirtyPathsCondensesListOfLists() {
        // Given multiple list items modified (more than one index)
        List<String> paths = List.of("vertices/0/0", "vertices/0/1", "vertices/1/0", "vertices/1/1");

        // When condensing the paths
        List<String> result = state.condenseDirtyPaths(paths);

        // Then paths should be condensed to wildcard syntax
        assertThat(result).containsExactly("vertices/*/*");
    }

    @Test
    public void condenseDirtyPathsHandlesMultipleProperties() {
        // Given multiple items with multiple properties modified
        List<String> paths = List.of("files/0/name", "files/0/size", "files/1/name", "files/1/size");

        // When condensing the paths
        List<String> result = state.condenseDirtyPaths(paths);

        // Then all properties should be condensed to wildcard syntax
        assertThat(result).containsExactlyInAnyOrder("files/*/name", "files/*/size");
    }

    @Test
    public void getKeyValuedStatesCombinesTransientAndRepositoryMatches() {
        DBSDocumentState match = mock(DBSDocumentState.class);
        doReturn("t1").when(match).getId();
        doReturn("wanted").when(match).get("k");
        DBSDocumentState noMatch = mock(DBSDocumentState.class);
        doReturn("t2").when(noMatch).getId();
        doReturn("other").when(noMatch).get("k");
        state.transientStates.put("t1", match);
        state.transientStates.put("t2", noMatch);

        State repoState = new State();
        repoState.put(SYS_ID, "r1");
        doReturn(List.of(repoState)).when(connection).queryKeyValue(eq("k"), eq("wanted"), anySet());

        List<DBSDocumentState> result = state.getKeyValuedStates("k", "wanted");

        assertEquals(2, result.size());
        assertTrue(result.contains(match));
        assertTrue(result.stream().anyMatch(s -> "r1".equals(s.getId())));
    }

    @Test
    public void getKeyValuedStatesWithTwoKeysCombinesMatches() {
        DBSDocumentState match = mock(DBSDocumentState.class);
        doReturn("t1").when(match).getId();
        doReturn("v1").when(match).get("k1");
        doReturn("v2").when(match).get("k2");
        DBSDocumentState noMatch = mock(DBSDocumentState.class);
        doReturn("t2").when(noMatch).getId();
        doReturn("v1").when(noMatch).get("k1");
        doReturn("nope").when(noMatch).get("k2");
        state.transientStates.put("t1", match);
        state.transientStates.put("t2", noMatch);

        State repoState = new State();
        repoState.put(SYS_ID, "r1");
        doReturn(List.of(repoState)).when(connection).queryKeyValue(eq("k1"), eq("v1"), eq("k2"), eq("v2"), anySet());

        List<DBSDocumentState> result = state.getKeyValuedStates("k1", "v1", "k2", "v2");

        assertEquals(2, result.size());
        assertTrue(result.contains(match));
        assertTrue(result.stream().anyMatch(s -> "r1".equals(s.getId())));
    }

    @Test
    public void afterCompletionSchedulesTasksOnCommit() {
        DBSTransactionState.TASKS.set(new ArrayList<>(List.of(mock(DocumentTask.class))));
        doNothing().when(state).scheduleTasks(anyList());

        state.afterCompletion(Status.STATUS_COMMITTED);

        verify(state).scheduleTasks(anyList());
    }

    @Test
    public void afterCompletionDoesNotScheduleTasksWhenNotCommitted() {
        DBSTransactionState.TASKS.set(new ArrayList<>(List.of(mock(DocumentTask.class))));

        state.afterCompletion(Status.STATUS_ROLLEDBACK);

        verify(state, never()).scheduleTasks(anyList());
    }
}
