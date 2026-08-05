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
package org.nuxeo.opensearch.io;

import java.util.Collection;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.model.EmbeddingModel;

/**
 * Contract used to parse an embeddings {@link Blob} and build a collection of {@link EmbeddingOSDTO}.
 * <p>
 * A single implementation can be contributed through the <code>embeddingsParser</code> extension point.
 * If none is contributed or the contributed implementation is disabled / fails to instantiate, the
 * runtime will transparently fall back to {@link #DEFAULT}.
 * </p>
 * <p>
 * The {@link #newParser()} implementation simply returns an empty collection (no embeddings extracted) and is
 * used as a fallback if no parser is contributed.
 * </p>
 */
public interface EmbeddingsParser {

    /**
     * Provides the default parser instance (returns an empty collection).
     */
    static EmbeddingsParser newParser() {
        return new ParquetEmbeddingsParser();
    }

    /**
     * Parses the provided blob and returns zero or more embedding DTO objects.
     *
     * @param blob the source blob holding raw embeddings (format implementation specific)
     * @param repositoryName the repository name owning the parent document
     * @param parentDocId the parent document id
     * @param embeddingsType logical type/category of the embeddings
     * @return a collection of parsed embeddings (never {@code null}); empty for {@link #newParser()}
     */
    Collection<EmbeddingModel> parse(Blob blob, String repositoryName, String parentDocId, String embeddingsType);

}
