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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.repository.PoolConfiguration;

class DBSRepositoryDescriptorTest {

    @Test
    void shouldReturnDefaultsWhenNothingConfigured() {
        DBSRepositoryDescriptor descriptor = new DBSRepositoryDescriptor();

        assertNull(descriptor.isDefault());
        assertNull(descriptor.isHeadless());
        assertFalse(descriptor.isFulltextSearchDisabled());
        assertFalse(descriptor.isFullTextStoredInBlob());
        assertFalse(descriptor.isCacheEnabled());
        assertEquals(DBSRepositoryDescriptor.FULLTEXT_FIELD_SIZE_LIMIT_DEFAULT,
                descriptor.getFulltextFieldSizeLimit());
    }

    @Test
    void shouldReflectConfiguredFlags() {
        DBSRepositoryDescriptor descriptor = new DBSRepositoryDescriptor();
        descriptor.isDefault = Boolean.TRUE;
        descriptor.headless = Boolean.TRUE;
        descriptor.isFulltextDisabled = Boolean.TRUE;
        descriptor.fulltextStoredInBlob = Boolean.TRUE;
        descriptor.setCacheEnabled(true);

        assertTrue(descriptor.isDefault());
        assertTrue(descriptor.isHeadless());
        assertTrue(descriptor.isFulltextSearchDisabled());
        assertTrue(descriptor.isFullTextStoredInBlob());
        assertTrue(descriptor.isCacheEnabled());
    }

    @Test
    void mergeShouldCopyNonNullScalarFields() {
        DBSRepositoryDescriptor base = new DBSRepositoryDescriptor();
        DBSRepositoryDescriptor other = new DBSRepositoryDescriptor();
        other.name = "repo2";
        other.label = "Label";
        other.isDefault = Boolean.TRUE;
        other.setCacheEnabled(true);
        other.cacheTTL = 5L;
        other.cacheMaxSize = 100L;
        other.cacheConcurrencyLevel = 4;
        other.clusterInvalidatorClass = DBSClusterInvalidator.class;

        base.merge(other);

        assertEquals("repo2", base.name);
        assertEquals("Label", base.label);
        assertTrue(base.isDefault());
        assertTrue(base.isCacheEnabled());
        assertEquals(5L, base.cacheTTL);
        assertEquals(100L, base.cacheMaxSize);
        assertEquals(4, base.cacheConcurrencyLevel);
        assertEquals(DBSClusterInvalidator.class, base.clusterInvalidatorClass);
    }

    @Test
    void mergeShouldKeepBaseWhenOtherFieldsAreNull() {
        DBSRepositoryDescriptor base = new DBSRepositoryDescriptor();
        base.name = "orig";
        base.label = "OrigLabel";

        base.merge(new DBSRepositoryDescriptor());

        assertEquals("orig", base.name);
        assertEquals("OrigLabel", base.label);
    }

    @Test
    void mergeShouldCopyPoolWhenBaseHasNone() {
        DBSRepositoryDescriptor base = new DBSRepositoryDescriptor();
        DBSRepositoryDescriptor other = new DBSRepositoryDescriptor();
        other.pool = new PoolConfiguration();

        base.merge(other);

        assertNotNull(base.pool);
        assertNotSame(other.pool, base.pool);
    }

    @Test
    void mergeShouldMergeIntoExistingPool() {
        DBSRepositoryDescriptor base = new DBSRepositoryDescriptor();
        base.pool = new PoolConfiguration();
        PoolConfiguration basePool = base.pool;
        DBSRepositoryDescriptor other = new DBSRepositoryDescriptor();
        other.pool = new PoolConfiguration();

        base.merge(other);

        assertSame(basePool, base.pool);
    }
}
