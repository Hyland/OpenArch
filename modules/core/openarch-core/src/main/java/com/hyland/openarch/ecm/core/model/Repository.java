/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.ecm.core.model;

import java.util.Collection;
import java.util.function.BiConsumer;

import com.hyland.openarch.ecm.core.schema.FulltextConfiguration;

/**
 * Interface to manage a low-level repository.
 */
public interface Repository {

    String getName();

    InternalSession getInternalSession();

    void shutdown();

    /**
     * Marks the blobs in use by passing them to the provided callback (taking the blob key and the repository name).
     * <p>
     * If key prefixes are provided, only the blob keys starting with one of them are passed to the callback. The keys
     * are the stored ones, so they may include a blob provider prefix ({@code providerId:}).
     *
     * @param markerCallback the callback
     * @param keyPrefixes the blob key prefixes to restrict to, or {@code null} for all blob keys
     * @since 2021.8
     */
    void markReferencedBlobs(BiConsumer<String, String> markerCallback, Collection<String> keyPrefixes);

    /**
     * Gets the fulltext configuration for this repository using its current content model, or {@code null} if disabled.
     *
     * @since 10.3 (already available since 8.1 for DBSRepository)
     */
    FulltextConfiguration getFulltextConfiguration();

}
