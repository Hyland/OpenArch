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
package com.hyland.openarch.opensearch.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.ChunkTexts;
import com.hyland.openarch.ecm.core.api.impl.blob.FileBlob;
import com.hyland.openarch.ecm.core.model.EmbeddingModel;
import com.hyland.openarch.opensearch.test.util.ParquetBlobGenerator;

/**
 * Covers what {@link ParquetEmbeddingsParser#parseChunkTexts(Blob, String)} returns for ids that are valid, non-UUID,
 * blank, or missing from the schema, including the invalid-id count. Runs without the OpenArch runtime, so blobs are
 * written to temp files directly.
 */
class ParquetEmbeddingsParserInvalidChunkIdTest {

    private static final String TYPE = "docs";
    private static final String VALID_UUID = "3f2a9c1e-5b7d-4e8a-9c2f-1a2b3c4d5e6f";
    private static final String REPOSITORY = "unit-repo";
    private static final String PARENT_DOC_ID = "parent-1";
    private static final String EMBEDDING_DOC_ID = "embedding-doc-1";

    private final ParquetEmbeddingsParser parser = new ParquetEmbeddingsParser();
    private final StandaloneGenerator generator = new StandaloneGenerator();

    @Test
    void validIdsKeepAllChunksWithNoInvalidIds() throws IOException {
        Blob chunks = generator.generateChunkTextsFromMap(Map.of(
                VALID_UUID, "text-a",
                "embedding-1", "text-b"));

        ChunkTexts result = parser.parseChunkTexts(chunks, TYPE);

        assertEquals(2, result.texts().size());
        assertTrue(result.texts().containsValue("text-a"));
        assertTrue(result.texts().containsValue("text-b"));
        assertEquals(0, result.invalidIds());
    }

    @Test
    void nonUuidIdsAreKeptAndNotCountedAsInvalid() throws IOException {
        // Non-UUID ids are a normal join key: they are scoped to a derived UUID and must not be reported.
        Blob chunks = generator.generateChunkTextsFromMap(Map.of(
                "embedding-1", "text-a",
                "abc", "text-b"));

        ChunkTexts result = parser.parseChunkTexts(chunks, TYPE);

        assertEquals(2, result.texts().size());
        assertTrue(result.texts().containsValue("text-a"));
        assertTrue(result.texts().containsValue("text-b"));
        assertEquals(0, result.invalidIds());
    }

    @Test
    void blankIdsAreKeptAndCounted() throws IOException {
        // Blank ids are still written to the map, under their derived ids, so all three texts are present.
        Blob chunks = generator.generateChunkTextsFromMap(Map.of(
                "", "text-a",
                "   ", "text-b",
                "embedding-1", "text-c"));

        ChunkTexts result = parser.parseChunkTexts(chunks, TYPE);

        assertEquals(3, result.texts().size());
        assertTrue(result.texts().containsValue("text-a"));
        assertTrue(result.texts().containsValue("text-b"));
        assertTrue(result.texts().containsValue("text-c"));
        assertEquals(2, result.invalidIds());
    }

    @Test
    void schemaMissingTextReturnsEmptyChunks() throws IOException {
        // generateWithoutText writes the embeddings schema without a text field.
        Blob chunks = generator.generateWithoutText(2, TYPE);

        ChunkTexts result = parser.parseChunkTexts(chunks, TYPE);

        assertTrue(result.texts().isEmpty());
        assertEquals(0, result.invalidIds());
    }

    @Test
    void twoBlobParseKeepsValidRowsWhenSomeChunkIdsAreInvalid() throws IOException {
        // Invalid rows are logged, not thrown: rows with valid ids still get their text.
        Blob embeddings = generator.generate(2, TYPE);
        Blob chunks = generator.generateChunkTextsFromMap(Map.of(
                "", "text-a",
                "embedding-1", "text-b"));

        Collection<EmbeddingModel> models = parser.parse(embeddings, chunks, REPOSITORY, PARENT_DOC_ID, TYPE,
                EMBEDDING_DOC_ID);

        assertEquals(2, models.size());
        assertTrue(models.stream().anyMatch(m -> "text-b".equals(m.text())),
                "The row with a valid id should still receive its chunk text");
    }

    @Test
    void twoBlobParseJoinsTextWhenChunkIdsAreValid() throws IOException {
        Blob embeddings = generator.generate(2, TYPE);
        Blob chunks = generator.generateChunkTextsFromMap(Map.of(
                "embedding-1", "text-a",
                "embedding-2", "text-b"));

        Collection<EmbeddingModel> models = parser.parse(embeddings, chunks, REPOSITORY, PARENT_DOC_ID, TYPE,
                EMBEDDING_DOC_ID);

        assertEquals(2, models.size());
        assertTrue(models.stream().allMatch(m -> m.text() != null), "Every vector row should receive its chunk text");
    }

    /** Runtime-free generator: writes temp files instead of going through {@code Blobs.createBlobWithExtension}. */
    private static final class StandaloneGenerator extends ParquetBlobGenerator {
        @Override
        protected Blob newParquetBlob() throws IOException {
            File file = Files.createTempFile("test-parquet-", PARQUET_FILE_EXTENSION).toFile();
            file.deleteOnExit();
            return new FileBlob(file, PARQUET_FILE_MIME_TYPE);
        }
    }
}
