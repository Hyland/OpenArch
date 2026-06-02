/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.core.model;

import java.util.function.BiConsumer;

import org.nuxeo.ecm.core.api.repository.FulltextConfiguration;

/**
 * Interface to manage a low-level repository.
 */
public interface Repository {

    String getName();

    InternalSession getInternalSession();

    void shutdown();

    /**
     * Marks the blobs in use by passing them to the provided callback (taking the blob key and the repository name).
     *
     * @since 2021.8
     */
    void markReferencedBlobs(BiConsumer<String, String> markerCallback);

    /**
     * Gets the fulltext configuration for this repository.
     *
     * @since 10.3 (already available since 8.1 for DBSRepository)
     */
    FulltextConfiguration getFulltextConfiguration();

    /**
     * Reload the FullText configuration, used after an update in the schema manager definition.
     */
    void reloadFullTextConfiguration(FulltextConfiguration updatedConfiguration);

}
