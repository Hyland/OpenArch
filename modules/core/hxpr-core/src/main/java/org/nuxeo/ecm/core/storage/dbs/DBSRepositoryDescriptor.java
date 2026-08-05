/*
 * (C) Copyright 2016-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.storage.dbs;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.registry.XRegistryId;
import org.nuxeo.ecm.core.api.repository.PoolConfiguration;

/**
 * DBS Repository Descriptor.
 *
 * @since 7.10-HF04, 8.1
 */
public class DBSRepositoryDescriptor {

    public static final int FULLTEXT_FIELD_SIZE_LIMIT_DEFAULT = 15 * 1024 * 1024; // 15MB

    @XNode("@name")
    @XRegistryId
    public String name;

    @XNode("@label")
    public String label;

    @XNode("@isDefault")
    protected Boolean isDefault;

    public Boolean isDefault() {
        return isDefault;
    }

    @XNode("@headless")
    protected Boolean headless;

    /** @since 11.2 */
    public Boolean isHeadless() {
        return headless;
    }

    @XNode("fulltext@disabled")
    public Boolean isFulltextDisabled;

    public boolean isFulltextSearchDisabled() {
        return defaultFalse(isFulltextDisabled);
    }

    @XNode("fulltext@fieldSizeLimit")
    private Integer fulltextFieldSizeLimit = FULLTEXT_FIELD_SIZE_LIMIT_DEFAULT;

    public int getFulltextFieldSizeLimit() {
        return fulltextFieldSizeLimit == null ? FULLTEXT_FIELD_SIZE_LIMIT_DEFAULT : fulltextFieldSizeLimit.intValue();
    }

    /** @since 11.1 */
    @XNode("fulltext@storedInBlob")
    public Boolean fulltextStoredInBlob;

    public boolean isFullTextStoredInBlob() {
        return defaultFalse(fulltextStoredInBlob);
    }

    /** @since 8.10 */
    @XNode("cache@enabled")
    private Boolean cacheEnabled;

    /** @since 8.10 */
    public boolean isCacheEnabled() {
        return defaultFalse(cacheEnabled);
    }

    /** @since 8.10 */
    protected void setCacheEnabled(boolean enabled) {
        cacheEnabled = Boolean.valueOf(enabled);
    }

    /** @since 8.10 */
    @XNode("cache@ttl")
    public Long cacheTTL;

    /** @since 8.10 */
    @XNode("cache@maxSize")
    public Long cacheMaxSize;

    /** @since 8.10 */
    @XNode("cache@concurrencyLevel")
    public Integer cacheConcurrencyLevel;

    /** @since 8.10 */
    @XNode("clustering/invalidatorClass")
    public Class<? extends DBSClusterInvalidator> clusterInvalidatorClass;

    @XNode("pool")
    public PoolConfiguration pool;

    public void merge(DBSRepositoryDescriptor other) {
        if (other.name != null) {
            name = other.name;
        }
        if (other.label != null) {
            label = other.label;
        }
        if (other.isDefault != null) {
            isDefault = other.isDefault;
        }
        if (other.pool != null) {
            if (pool == null) {
                pool = new PoolConfiguration(other.pool);
            } else {
                pool.merge(other.pool);
            }
        }
        if (other.cacheEnabled != null) {
            cacheEnabled = other.cacheEnabled;
        }
        if (other.cacheTTL != null) {
            cacheTTL = other.cacheTTL;
        }
        if (other.cacheMaxSize != null) {
            cacheMaxSize = other.cacheMaxSize;
        }
        if (other.cacheConcurrencyLevel != null) {
            cacheConcurrencyLevel = other.cacheConcurrencyLevel;
        }
        if (other.clusterInvalidatorClass != null) {
            clusterInvalidatorClass = other.clusterInvalidatorClass;
        }
    }

    private static boolean defaultFalse(Boolean bool) {
        return Boolean.TRUE.equals(bool);
    }

}
