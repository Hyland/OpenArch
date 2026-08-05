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
package org.nuxeo.ecm.core.schema;

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
     *
     * @param repositoryName the repository
     * @param schemaManager the schema manager
     */
    void setSchemaManager(String repositoryName, SchemaManager schemaManager);

    /**
     * CDI event sent when the schema manager for a repository changes.
     */
    record SchemaManagerUpdatedEvent(String repositoryName) {
    }

}
