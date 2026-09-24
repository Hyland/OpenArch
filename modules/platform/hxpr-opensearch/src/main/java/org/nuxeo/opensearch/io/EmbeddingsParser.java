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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.model.EmbeddingModel;

/**
 * Contract used to parse an embeddings {@link Blob} and build a collection of {@link EmbeddingOSDTO}.
 * <p>
 * A single implementation can be contributed through the <code>embeddingsParser</code> extension point.
 * If none is contributed or the contributed implementation is disabled / fails to instantiate, the
 * runtime will transparently fall back to {@link #newParser()}.
 * </p>
 */
public interface EmbeddingsParser {

    /**
     * Provides the default parser instance.
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

    /**
     * Parses the embeddings blob, and when a chunks blob is also provided, overlays its text onto the parsed
     * records by joining on embedding id.
     * <p>
     * When {@code chunksBlob} is null the text in the embeddings blob is used. When it is non-null but the
     * parser doesn't recognise its format (default {@link #parseChunkTexts} returns empty), text is cleared
     * rather than falling back to the embeddings blob - a caller that supplied a chunks blob has declared
     * that shape, and fabricating text from the other side would mask a delivery bug. Implementations that
     * carry more than {@code {id, text}} in the chunks blob should override {@link #parseChunkTexts}.
     * </p>
     */
    default Collection<EmbeddingModel> parse(Blob embeddingsBlob, Blob chunksBlob, String repositoryName,
            String parentDocId, String embeddingsType) {
        Collection<EmbeddingModel> parsed = parse(embeddingsBlob, repositoryName, parentDocId, embeddingsType);
        if (chunksBlob == null) {
            return parsed;
        }
        Map<String, String> chunkTexts = parseChunkTexts(chunksBlob, embeddingsType);
        List<EmbeddingModel> merged = new ArrayList<>(parsed.size());
        for (EmbeddingModel m : parsed) {
            merged.add(new EmbeddingModel(m.id(), m.repository(), m.docId(), m.type(), m.vector(),
                    chunkTexts.get(m.id()), m.location(), m.blobRef()));
        }
        return merged;
    }

    /**
     * Extracts an id -> chunk text map from a chunk-text blob. Returns an empty map when the blob is null,
     * empty, or in an unrecognised format. Keys must match the ids produced by
     * {@link #parse(Blob, String, String, String)} so callers can join without further conversion.
     */
    default Map<String, String> parseChunkTexts(Blob chunksBlob, String embeddingsType) {
        return Map.of();
    }

}
