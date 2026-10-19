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

import com.hyland.openarch.ecm.core.api.lock.LockManager;
import com.hyland.openarch.ecm.core.model.Repository;
import com.hyland.openarch.ecm.core.schema.SchemaManager;

/**
 * Interface for a {@link Repository} for Document-Based Storage.
 *
 * @since 5.9.4
 */
public interface DBSRepository extends Repository, LockManager {

    /**
     * Gets a new connection to this repository.
     *
     * @return a new connection
     * @since 11.1
     */
    DBSConnection getConnection();

    /**
     * Checks if fulltext indexing (and search) is disabled.
     *
     * @return {@code true} if fulltext indexing is disabled, {@code false} if it is enabled
     * @since 7.1, 6.0-HF02
     */
    boolean isFulltextDisabled();

    /**
     * Checks if fulltext is stored in a blob.
     *
     * @return {@code true} if fulltext is stored in a blob, {@code false} if it is stored as a regular string
     * @since 11.1
     */
    boolean isFulltextStoredInBlob();

    /**
     * Checks whether this repository supports transactions.
     *
     * @return {@code true} if the repository supports transactions
     * @since 11.1
     * @see DBSConnection#begin
     * @see DBSConnection#commit
     * @see DBSConnection#rollback
     */
    boolean supportsTransactions();

    /**
     * Gets the schema manager for this repository.
     */
    SchemaManager getSchemaManager();

}
