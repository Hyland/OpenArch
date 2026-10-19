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
package com.hyland.openarch.ecm.core.storage.dbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.hyland.openarch.ecm.core.schema.FulltextConfiguration;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.SchemaManagerBase;

class DBSRepositoryBaseTest {

    @Test
    void fulltextConfigurationUsesCurrentSchemaManagerAndRepositorySettings() {
        DBSRepositoryBase repository = mock(DBSRepositoryBase.class, CALLS_REAL_METHODS);
        DBSRepositoryDescriptor descriptor = new DBSRepositoryDescriptor();
        descriptor.fulltextStoredInBlob = true;
        repository.initialize(descriptor);
        SchemaManager schemaManager = mock(SchemaManager.class);
        FulltextConfiguration indexConfiguration = FulltextConfiguration.fromSchemaManager(new SchemaManagerBase());
        when(schemaManager.getFulltextConfiguration()).thenReturn(indexConfiguration);
        doReturn(schemaManager).when(repository).getSchemaManager();
        var configuration = repository.getFulltextConfiguration();
        assertFalse(repository.isFulltextDisabled());
        assertTrue(repository.isFulltextStoredInBlob());
        assertTrue(configuration.storedInBlob());
        assertEquals(descriptor.getFulltextFieldSizeLimit(), configuration.fieldSizeLimit());
        assertEquals(indexConfiguration.propPathBlobProperties(), configuration.propPathBlobProperties());

        SchemaManager updatedManager = mock(SchemaManager.class);
        FulltextConfiguration updatedIndex = mock(FulltextConfiguration.class);
        when(updatedIndex.withFieldSizeLimitAndStoredInBlob(descriptor.getFulltextFieldSizeLimit(), true))
                .thenReturn(updatedIndex);
        when(updatedManager.getFulltextConfiguration()).thenReturn(updatedIndex);
        when(updatedIndex.isFulltextIndexable("NewType")).thenReturn(true);
        doReturn(updatedManager).when(repository).getSchemaManager();
        assertTrue(repository.getFulltextConfiguration().isFulltextIndexable("NewType"));
        assertFalse(configuration.isFulltextIndexable("NewType"));
    }

    @Test
    void disabledFulltextDoesNotUseTheSchemaManagerOrStoreFulltextInBlob() {
        DBSRepositoryBase repository = mock(DBSRepositoryBase.class, CALLS_REAL_METHODS);
        DBSRepositoryDescriptor descriptor = new DBSRepositoryDescriptor();
        descriptor.isFulltextDisabled = true;
        descriptor.fulltextStoredInBlob = true;
        repository.initialize(descriptor);
        assertTrue(repository.isFulltextDisabled());
        assertFalse(repository.isFulltextStoredInBlob());
        assertNull(repository.getFulltextConfiguration());
    }
}
