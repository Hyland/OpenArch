/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.schema;

import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;

/**
 * The TypeService is the component dealing with registration of schemas and document types (and mixins).
 */
public interface TypeService {

    /**
     * Gets the default schema manager.
     *
     * @return the default schema manager
     */
    SchemaManager getDefaultSchemaManager();

    /**
     * Gets the schema manager for the given repository.
     *
     * @param repositoryName the repository
     * @return the schema manager for the given repository
     */
    SchemaManager getSchemaManager(String repositoryName);

    /**
     * Sets a new schema manager for the given repository.
     * <p>
     * If the resulting configuration is identical to the persisted one, nothing is written.
     *
     * @param repositoryName the repository
     * @param schemaManager the schema manager
     */
    void setSchemaManager(String repositoryName, SchemaManager schemaManager);

    /**
     * Sets a new schema manager for the given repository using optimistic locking.
     * <p>
     * The new configuration is persisted only if the persisted version still matches {@code expectedVersion}; the local
     * schema manager is updated only after a successful persist. If the persisted version has moved, a concurrent-update
     * exception (HTTP 409) is thrown and the local state is left unchanged.
     * <p>
     * If the resulting configuration is identical to the persisted one, nothing is written and the current version is
     * returned unchanged, so repeatedly applying the same content model is free and cannot conflict. A stale
     * {@code expectedVersion} is still rejected, even when the content matches.
     *
     * @param repositoryName the repository
     * @param schemaManager the schema manager
     * @param expectedVersion the version the caller last read (see {@link #getModelWithVersion})
     * @throws RuntimeException a concurrent-update exception (HTTP 409) if the persisted version no longer matches
     *             {@code expectedVersion}
     */
    void setSchemaManager(String repositoryName, SchemaManager schemaManager, long expectedVersion);

    /**
     * Gets the persisted content model together with its optimistic-locking version for the given repository.
     * <p>
     * The returned model and version form a consistent snapshot suitable for a read-modify-write cycle: read the model
     * and version, compute an update, then write it back with {@link #setSchemaManager(String, SchemaManager, long)}.
     *
     * @param repositoryName the repository
     * @return the content model and its version
     */
    ModelWithVersion getModelWithVersion(String repositoryName);

    /**
     * A content model together with its optimistic-locking version.
     *
     * @param model the content model (carrying no version of its own)
     * @param version the optimistic-locking version
     */
    record ModelWithVersion(ContentModelDTO model, long version) {
    }

}
