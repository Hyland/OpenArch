/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
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

import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_BOTTOM;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_COLUMN;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_END;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_LEFT;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_PAGE;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_PARAGRAPH;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_POSITION;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_RIGHT;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_ROW;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_SHEET;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_SPREADSHEET;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_START;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_TEXT;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_TIMESTAMP;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_TOP;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_BLOB_REF;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_ID;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_LOCATION;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TEXT;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TYPE;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_VECTOR;

import java.io.IOException;

import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.model.EmbeddingModel;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;

/**
 * OpenSearch DTO for an embedding with denormalized parent document metadata.
 * <p>
 * This DTO includes both the embedding data and the parent document metadata to avoid expensive parent-child joins at
 * query time.
 */
public class EmbeddingOSDTO implements JsonpSerializable {

    private final EmbeddingModel embedding;

    private final DocumentModel parentDoc;

    /**
     * Constructor for embedding with denormalized parent document metadata.
     *
     * @param embedding the embedding model
     * @param parentDoc the parent document (required for denormalization)
     */
    public EmbeddingOSDTO(EmbeddingModel embedding, DocumentModel parentDoc) {
        this.embedding = embedding;
        this.parentDoc = parentDoc;
    }

    /**
     * @return the embedding model id
     */
    @JsonIgnore
    public String getId() {
        return embedding.id();
    }

    /**
     * Calculates the serialized size of this DTO in bytes.
     *
     * @param jsonProvider the JSON provider to use for serialization
     * @return the size in bytes
     */
    public int size(JsonProvider jsonProvider) {
        try (CountingOutputStream countingStream = new CountingOutputStream(NullOutputStream.INSTANCE);
                JsonGenerator generator = jsonProvider.createGenerator(countingStream)) {
            serialize(generator, null);
            generator.flush();
            return Math.toIntExact(countingStream.getByteCount());
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IOException from CountingOutputStream", e);
        }
    }

    @Override
    public void serialize(JsonGenerator jg, JsonpMapper mapper) {
        if (parentDoc == null) {
            throw new IllegalStateException(
                    "parentDoc is required for embedding serialization. Embeddings must be indexed with denormalized parent data.");
        }

        jg.writeStartObject();

        // Write embedding-specific fields
        jg.write(EMBEDDING_PROPERTY_ID, embedding.id());
        jg.write(EMBEDDING_PROPERTY_TYPE, embedding.type());

        if (embedding.text() != null) {
            jg.writeKey(EMBEDDING_PROPERTY_TEXT);
            jg.write(embedding.text());
        }

        if (embedding.blobRef() != null) {
            jg.writeKey(EMBEDDING_PROPERTY_BLOB_REF);
            jg.write(embedding.blobRef());
        }

        jg.writeStartArray(EMBEDDING_PROPERTY_VECTOR);
        embedding.vector().forEach(jg::write);
        jg.writeEnd();

        if (embedding.location() != null) {
            jg.writeStartObject(EMBEDDING_PROPERTY_LOCATION);

            if (embedding.location().text() != null) {
                jg.writeStartObject(EMBEDDING_FIELD_LOCATION_TEXT)
                  .write(EMBEDDING_FIELD_LOCATION_PAGE, embedding.location().text().page())
                  .write(EMBEDDING_FIELD_LOCATION_PARAGRAPH, embedding.location().text().paragraph())
                  .writeEnd();
            }

            if (embedding.location().position() != null) {
                jg.writeStartObject(EMBEDDING_FIELD_LOCATION_POSITION)
                  .write(EMBEDDING_FIELD_LOCATION_TOP, embedding.location().position().top())
                  .write(EMBEDDING_FIELD_LOCATION_LEFT, embedding.location().position().left())
                  .write(EMBEDDING_FIELD_LOCATION_RIGHT, embedding.location().position().right())
                  .write(EMBEDDING_FIELD_LOCATION_BOTTOM, embedding.location().position().bottom())
                  .writeEnd();
            }

            if (embedding.location().timestamp() != null) {
                jg.writeStartObject(EMBEDDING_FIELD_LOCATION_TIMESTAMP)
                  .write(EMBEDDING_FIELD_LOCATION_START, embedding.location().timestamp().start())
                  .write(EMBEDDING_FIELD_LOCATION_END, embedding.location().timestamp().end())
                  .writeEnd();
            }

            if (embedding.location().spreadsheet() != null) {
                jg.writeStartObject(EMBEDDING_FIELD_LOCATION_SPREADSHEET)
                  .write(EMBEDDING_FIELD_LOCATION_SHEET, embedding.location().spreadsheet().sheet())
                  .write(EMBEDDING_FIELD_LOCATION_ROW, embedding.location().spreadsheet().row())
                  .write(EMBEDDING_FIELD_LOCATION_COLUMN, embedding.location().spreadsheet().column())
                  .writeEnd();
            }

            jg.writeEnd();
        }

        // Write denormalized parent document metadata
        writeParentDocumentMetadata(jg);

        jg.writeEnd();
    }

    /**
     * Writes denormalized parent document metadata. This includes all document system properties and custom properties,
     * EXCEPT for: - Fulltext binary content (too large) - Join fields (not needed in denormalized data)
     * <p>
     * Note: parentDoc is guaranteed non-null by the check in serialize().
     */
    private void writeParentDocumentMetadata(JsonGenerator jg) {
        // Use specialized writer that excludes join fields and fulltext
        DenormalizedDocumentWriter writer = new DenormalizedDocumentWriter(jg);

        // Write system properties (automatically excludes join fields and fulltext)
        writer.writeSystemProperties(parentDoc);

        // Write custom properties
        writer.writeProperties(parentDoc);
    }
}
