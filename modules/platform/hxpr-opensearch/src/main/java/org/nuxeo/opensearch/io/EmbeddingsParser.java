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
