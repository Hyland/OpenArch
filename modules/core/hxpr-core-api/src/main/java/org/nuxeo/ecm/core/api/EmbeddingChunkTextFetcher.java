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
package org.nuxeo.ecm.core.api;

import java.util.Collection;
import java.util.Map;

/**
 * Resolves chunk text for embeddings whose text is not carried in the OpenSearch
 * {@code _source} - typically those written with {@code sysemb_residency = onPrem}.
 * <p>
 * The community read path publishes this contract; a residency-aware module
 * contributes the implementation. When no implementation is registered, the
 * caller receives an empty map and omits the affected hits from responses.
 */
public interface EmbeddingChunkTextFetcher {

    /**
     * Resolves chunk text for embedding ids belonging to a single source. The
     * {@code sourceId} is the {@code cin_sourceId} of the parent document and
     * scopes the lookup to one external system integration. Implementations must
     * return a non-null map; ids without a retrievable body are absent from the
     * map, which callers treat as omitted rather than as errors. Implementations
     * may throw unchecked exceptions for unrecoverable failures (network, auth,
     * protocol violations); the caller propagates such failures.
     */
    Map<String, String> fetchTexts(String sourceId, Collection<String> embeddingIds);
}
