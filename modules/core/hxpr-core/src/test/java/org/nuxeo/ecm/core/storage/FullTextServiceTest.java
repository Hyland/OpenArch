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
package org.nuxeo.ecm.core.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nuxeo.ecm.core.api.repository.FulltextConfiguration;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.repository.RepositoryService;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeService;
import com.hyland.openarch.runtime.api.Framework;

class FullTextServiceTest {

    private static final String REPO = "test";

    @Test
    void shouldReloadConfigurationOnHappyPath() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        Repository repository = mock(Repository.class);
        FulltextConfiguration fulltextConfiguration = mock(FulltextConfiguration.class);
        TypeService typeService = mock(TypeService.class);
        SchemaManager schemaManager = mock(SchemaManager.class);
        FullTextConfigurationService configService = mock(FullTextConfigurationService.class);
        FulltextConfiguration updated = mock(FulltextConfiguration.class);

        when(repositoryService.getRepository(REPO)).thenReturn(repository);
        when(repository.getFulltextConfiguration()).thenReturn(fulltextConfiguration);
        when(fulltextConfiguration.getFulltextFieldSizeLimit()).thenReturn(1024);
        when(fulltextConfiguration.isFulltextStoredInBlob()).thenReturn(true);
        when(typeService.getSchemaManager(REPO)).thenReturn(schemaManager);
        when(configService.makeConfiguration(schemaManager, 1024, true)).thenReturn(updated);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(RepositoryService.class)).thenReturn(repositoryService);
            framework.when(() -> Framework.getService(TypeService.class)).thenReturn(typeService);
            framework.when(() -> Framework.getService(FullTextConfigurationService.class)).thenReturn(configService);

            new FullTextService().reloadFullTextConfiguration(REPO);
        }

        verify(repository).reloadFullTextConfiguration(updated);
    }

    @Test
    void shouldDoNothingWhenRepositoryMissing() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(repositoryService.getRepository(REPO)).thenReturn(null);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(RepositoryService.class)).thenReturn(repositoryService);

            new FullTextService().reloadFullTextConfiguration(REPO);
        }

        verify(repositoryService).getRepository(REPO);
    }

    @Test
    void shouldDoNothingWhenFulltextConfigurationMissing() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        Repository repository = mock(Repository.class);
        when(repositoryService.getRepository(REPO)).thenReturn(repository);
        when(repository.getFulltextConfiguration()).thenReturn(null);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(RepositoryService.class)).thenReturn(repositoryService);

            new FullTextService().reloadFullTextConfiguration(REPO);
        }

        verify(repository, never()).reloadFullTextConfiguration(any());
    }

    @Test
    void onSchemaUpdatedShouldReloadForEventRepository() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(repositoryService.getRepository(REPO)).thenReturn(null);

        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(RepositoryService.class)).thenReturn(repositoryService);

            new FullTextService().onSchemaUpdated(new TypeService.SchemaManagerUpdatedEvent(REPO));
        }

        verify(repositoryService).getRepository(REPO);
    }
}
