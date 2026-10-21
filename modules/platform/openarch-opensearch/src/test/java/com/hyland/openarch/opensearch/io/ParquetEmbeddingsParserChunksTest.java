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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.ChunkTexts;
import com.hyland.openarch.ecm.core.api.impl.blob.FileBlob;
import com.hyland.openarch.ecm.core.api.impl.blob.StringBlob;
import com.hyland.openarch.ecm.core.model.EmbeddingModel;
import com.hyland.openarch.opensearch.test.util.ParquetBlobGenerator;

/**
 * Unit-level coverage for the two-blob {@link ParquetEmbeddingsParser#parse(Blob, Blob, String, String, String)}
 * overload and {@link ParquetEmbeddingsParser#parseChunkTexts(Blob, String)}. Runs without the OpenArch runtime by
 * subclassing {@link ParquetBlobGenerator} with a runtime-free blob factory. Integration coverage lives in
 * {@code TestEmbeddingsIndexing}; this class targets the branches Sonar flagged (null/empty/unreadable chunks
 * blob, partial chunk coverage, default interface methods).
 */
class ParquetEmbeddingsParserChunksTest {

    private static final String TYPE = "unitTestType";

    private static final String REPO = "unit-repo";

    private final ParquetEmbeddingsParser parser = new ParquetEmbeddingsParser();

    private final StandaloneParquetBlobGenerator generator = new StandaloneParquetBlobGenerator();

    private final String parentId = UUID.randomUUID().toString();
    private final String embeddingDocId = UUID.randomUUID().toString();

    // ---- tests ----

    @Test
    void twoBlobParseOverlaysChunkTextOntoEmbeddings() throws IOException {
        Blob emb = generator.generate(3, TYPE);
        Blob chunks = generator.generateChunkTexts(3, i -> "text-" + (i + 1));

        Collection<EmbeddingModel> models = parser.parse(emb, chunks, REPO, parentId, TYPE, embeddingDocId);

        assertEquals(3, models.size());
        List<String> texts = models.stream().map(EmbeddingModel::text).toList();
        assertTrue(texts.contains("text-1") && texts.contains("text-2") && texts.contains("text-3"),
                "Chunk-file texts should overlay onto embedding records; got " + texts);
    }

    @Test
    void twoBlobParseWithNullChunksReturnsEmbeddingsBlobText() throws IOException {
        Blob emb = generator.generate(2, TYPE);

        Collection<EmbeddingModel> models = parser.parse(emb, null, REPO, parentId, TYPE, embeddingDocId);

        assertEquals(2, models.size());
        models.forEach(m -> {
            assertNotNull(m.text());
            assertTrue(m.text().startsWith(TYPE + " - "), "Expected embeddings-blob text, got " + m.text());
        });
    }

    @Test
    void twoBlobParseWithEmptyChunksMapClearsText() throws IOException {
        Blob emb = generator.generate(2, TYPE);
        Blob emptyChunks = generator.generateChunkTexts(0, i -> "");

        Collection<EmbeddingModel> models = parser.parse(emb, emptyChunks, REPO, parentId, TYPE, embeddingDocId);

        assertEquals(2, models.size());
        models.forEach(m -> assertNull(m.text(),
                "An empty chunks blob is an explicit 'no text'; text must be cleared, not fabricated from embeddings blob"));
    }

    @Test
    void twoBlobParseLeavesTextNullForIdsAbsentFromChunks() throws IOException {
        Blob emb = generator.generate(4, TYPE);
        // Only cover the first two raw ids.
        Blob chunks = generator.generateChunkTextsFromMap(Map.of(
                generator.getRawEmbeddingIdForIndex(1), "covered-1",
                generator.getRawEmbeddingIdForIndex(2), "covered-2"));

        Collection<EmbeddingModel> models = parser.parse(emb, chunks, REPO, parentId, TYPE, embeddingDocId);

        assertEquals(4, models.size());
        long withText = models.stream().filter(m -> m.text() != null).count();
        assertEquals(2, withText, "Only ids covered by the chunks blob should have text");
    }

    @Test
    void parseChunkTextsReturnsEmptyForNullBlob() {
        assertTrue(parser.parseChunkTexts(null, TYPE).texts().isEmpty());
    }

    @Test
    void parseChunkTextsReturnsEmptyForZeroLengthBlob() {
        assertTrue(parser.parseChunkTexts(new StringBlob(""), TYPE).texts().isEmpty());
    }

    @Test
    void parseChunkTextsReturnsEmptyOnUnreadableBlob() {
        Blob junk = new StringBlob("not a parquet file at all");
        assertTrue(parser.parseChunkTexts(junk, TYPE).texts().isEmpty(),
                "Unreadable blob should be treated as 'no text', not throw");
    }

    @Test
    void twoBlobParseReturnsEmptyWhenEmbeddingsBlobUnreadable() {
        Blob junk = new StringBlob("not a parquet file");
        assertTrue(parser.parse(junk, junk, REPO, parentId, TYPE, embeddingDocId).isEmpty());
    }

    /**
     * The interface exposes default implementations for the two-blob {@code parse} and
     * {@code parseChunkTexts} overloads so custom parser contributions only need to implement single-blob
     * {@code parse}. This test hits those defaults through an anonymous minimal parser.
     */
    @Test
    void defaultInterfaceMethodsFallBackToSingleBlobParse() throws IOException {
        Blob emb = generator.generate(2, TYPE);
        EmbeddingsParser minimal = new EmbeddingsParser() {
            @Override
            public Collection<EmbeddingModel> parse(Blob blob, String repositoryName, String parentDocId,
                    String embeddingsType) {
                return new ParquetEmbeddingsParser().parse(blob, repositoryName, parentDocId, embeddingsType);
            }
        };

        Collection<EmbeddingModel> viaDefault = minimal.parse(emb, /* chunksBlob */ null, REPO, parentId, TYPE, embeddingDocId);
        assertEquals(2, viaDefault.size());

        ChunkTexts defaultChunks = minimal.parseChunkTexts(emb, TYPE);
        assertNotNull(defaultChunks);
        assertTrue(defaultChunks.texts().isEmpty(), "Default parseChunkTexts must return empty map");
    }

    /**
     * Runtime-free variant of {@link ParquetBlobGenerator}: creates temp files directly instead of routing
     * through {@code Blobs.createBlobWithExtension}, which needs the OpenArch runtime for file tracking. Every
     * other behaviour (schema, record shape, id conventions) is reused from the parent so this test stays in
     * sync with the integration test.
     */
    private static final class StandaloneParquetBlobGenerator extends ParquetBlobGenerator {
        @Override
        protected Blob newParquetBlob() throws IOException {
            File file = Files.createTempFile("test-parquet-", PARQUET_FILE_EXTENSION).toFile();
            file.deleteOnExit();
            return new FileBlob(file, PARQUET_FILE_MIME_TYPE);
        }
    }
}
