/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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



import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.lock.LockManager;
import com.hyland.openarch.ecm.core.schema.FulltextConfiguration;
import com.hyland.openarch.ecm.core.model.InternalSession;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.TypeService;
import com.hyland.openarch.runtime.api.Framework;

/**
 * Provides sharing behavior for repository sessions and other basic functions.
 *
 * @since 5.9.4
 */
public abstract class DBSRepositoryBase implements DBSRepository {

    // change to have deterministic pseudo-UUID generation for debugging
    public static final boolean DEBUG_UUIDS = false;

    public static final String UUID_ZERO = "00000000-0000-0000-0000-000000000000";

    public static final String UUID_ZERO_DEBUG = "UUID_0";

    protected final String repositoryName;

    protected boolean fulltextDisabled;

    protected boolean fulltextStoredInBlob;

    protected int fulltextFieldSizeLimit;

    protected LockManager lockManager;

    /**
     * Constructor. Callers must still call {@link #initialize}.
     */
    protected DBSRepositoryBase(String repositoryName, DBSRepositoryDescriptor descriptor) {
        this.repositoryName = repositoryName;
    }

    protected void initialize(DBSRepositoryDescriptor descriptor) {
        fulltextDisabled = descriptor.isFulltextSearchDisabled();
        fulltextStoredInBlob = !fulltextDisabled && descriptor.isFullTextStoredInBlob();
        fulltextFieldSizeLimit = descriptor.getFulltextFieldSizeLimit();
    }

    @Override
    public SchemaManager getSchemaManager() {
        return Framework.getService(TypeService.class).getSchemaManager(repositoryName);
    }

    @Override
    public void shutdown() {
    }

    @Override
    public String getName() {
        return repositoryName;
    }

    @Override
    public FulltextConfiguration getFulltextConfiguration() {
        return fulltextDisabled ? null
                : getSchemaManager().getFulltextConfiguration()
                                    .withFieldSizeLimitAndStoredInBlob(fulltextFieldSizeLimit, fulltextStoredInBlob);
    }

    @Override
    public Lock getLock(String id) {
        try (DBSConnection connection = getConnection()) {
            return connection.getLock(id);
        }
    }

    @Override
    public Lock setLock(String id, Lock lock) {
        try (DBSConnection connection = getConnection()) {
            return connection.setLock(id, lock);
        }
    }

    @Override
    public Lock removeLock(String id, String owner) {
        try (DBSConnection connection = getConnection()) {
            return connection.removeLock(id, owner);
        }
    }

    @Override
    public boolean isFulltextDisabled() {
        return fulltextDisabled;
    }

    @Override
    public boolean isFulltextStoredInBlob() {
        return fulltextStoredInBlob;
    }

    @Override
    public InternalSession getInternalSession() {
        return new DBSSession(this);
    }

}
