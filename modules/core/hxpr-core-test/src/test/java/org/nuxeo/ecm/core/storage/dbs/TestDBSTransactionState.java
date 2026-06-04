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
 *
 * Contributors:
 *     Gildas Lefevre
 */
package org.nuxeo.ecm.core.storage.dbs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.nuxeo.ecm.core.api.repository.FulltextConfiguration;
import org.nuxeo.ecm.core.security.SecurityPolicyService;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;

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

    @BeforeEach
    public void setUp() {
        when(securityService.getSecurityPolicyService(REPO_ID)).thenReturn(securityPolicyService);
        doReturn(REPO_ID).when(repository).getName();
        FulltextConfiguration mockFulltextConfiguration = mock(FulltextConfiguration.class);
        doReturn(mockFulltextConfiguration).when(repository).getFulltextConfiguration();
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
}
