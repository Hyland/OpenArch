/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.model.EmbeddingModel.VECTOR_SIZE;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.apache.avro.generic.GenericData;
import org.junit.jupiter.api.Test;
import org.nuxeo.common.utils.IdUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.model.EmbeddingModel;
import org.nuxeo.ecm.core.model.LocationModel;

public class ParquetEmbeddingsParserTest {

    private static final ClassLoader classLoader = ParquetEmbeddingsParserTest.class.getClassLoader();

    public static final String TEST_EMBEDDINGS_PARQUET_FILENAME = "test-embeddings.parquet";

    @Test
    void parseShouldReturnExpectedEmbeddings() {
        File parquetFile = fileOf(TEST_EMBEDDINGS_PARQUET_FILENAME);
        assertTrue(parquetFile.exists(), "The test parquet file must exist");

        Blob blob = Blobs.createBlob(parquetFile);
        assertNotNull(blob, "The blob must not be null");

        ParquetEmbeddingsParser parser = new ParquetEmbeddingsParser();
        String parentId = UUID.randomUUID().toString();
        String embeddingsType = "goat_twitter_3";
        List<EmbeddingModel> embeddings = parser.parse(blob, "repository", parentId,
                embeddingsType).stream().toList();

        assertEquals(3, embeddings.size(), "Unexpected embedding count");

        List<ExpectedEmbedding> expected = List.of(
            new ExpectedEmbedding("embedding-1", embeddingsType, "Lorem ipsum dolor sit amet.",
                List.of(0.12, 0.34, 0.56, 0.78), LocationExpectation.standard()),
            new ExpectedEmbedding("embedding-2", embeddingsType, "Sample indexing text.",
                List.of(0.01, 0.02, 0.03, 0.04), LocationExpectation.standard()),
            new ExpectedEmbedding("embedding-3", embeddingsType, "Cell A1 content",
                List.of(0.9, 0.8, 0.7), LocationExpectation.standard()));

        for (int i = 0; i < embeddings.size(); i++) {
            EmbeddingModel model = embeddings.get(i);
            ExpectedEmbedding expectedEntry = expected.get(i);

            assertEquals(expectedEntry.uuid(), model.id());
            assertEquals(parentId, model.docId());
            assertEquals(expectedEntry.type(), model.type());
            assertEquals(expectedEntry.text(), model.text());

            List<Double> expectedVector = expectedEntry.vector();
            assertEquals(VECTOR_SIZE, model.vector().size()); // EmbeddingModel pads to VECTOR_SIZE

            // Check that the original vector values are preserved
            for (int j = 0; j < expectedVector.size(); j++) {
                assertEquals(expectedVector.get(j), model.vector().get(j));
            }

            // Check that the padding zeros are present for the rest
            for (int j = expectedVector.size(); j < VECTOR_SIZE; j++) {
                assertEquals(0.0, model.vector().get(j));
            }
            expectedEntry.location().assertMatches(model.location());
        }
    }

    @Test
    void parseShouldAssignDeterministicUuidWhenMissing() {
        Blob blob = blobOf(TEST_EMBEDDINGS_PARQUET_FILENAME);

        ParquetEmbeddingsParser parser = new ParquetEmbeddingsParser();
        List<EmbeddingModel> embeddings = parser.parse(blob, "repository", UUID.randomUUID().toString(), "type")
                .stream()
                .toList();

        assertEquals(3, embeddings.size());
        embeddings.forEach(embedding -> assertTrue(IdUtils.isValidUUID(embedding.id())));
    }

    @Test
    void toDoubleShouldHandleNumberAndNestedRecord() {
        ParquetEmbeddingsParser parser = new ParquetEmbeddingsParser();

        // Test direct Number
        assertEquals(1.5, parser.toDouble(1.5));
        assertEquals(42.0, parser.toDouble(42));

        // Test nested GenericData.Record with element field
        GenericData.Record record = new GenericData.Record(
                org.apache.avro.SchemaBuilder.record("element")
                                             .fields()
                                             .name("element").type().doubleType().noDefault()
                                             .endRecord()
        );
        record.put("element", 3.14);
        assertEquals(3.14, parser.toDouble(record));
    }

    @Test
    void toVectorShouldHandleGenericArrayAndCollection() {
        Blob blob = blobOf(TEST_EMBEDDINGS_PARQUET_FILENAME);
        ParquetEmbeddingsParser parser = new ParquetEmbeddingsParser();

        List<EmbeddingModel> embeddings = parser.parse(blob, "repository", UUID.randomUUID().toString(), "type")
                                                .stream()
                                                .toList();

        // Verify the first embedding's vector (from GenericArray in Parquet)
        assertFalse(embeddings.isEmpty());
        EmbeddingModel firstEmbedding = embeddings.get(0);

        // The vector should contain the original values followed by zeros
        assertEquals(0.12, firstEmbedding.vector().get(0));
        assertEquals(0.34, firstEmbedding.vector().get(1));
        assertEquals(0.56, firstEmbedding.vector().get(2));
        assertEquals(0.78, firstEmbedding.vector().get(3));
    }

    // Helper methods

    /**
     * Returns a test {@link Blob} created from the test embeddings Parquet file.
     *
     * @return a {@link Blob} containing the test embeddings data
     */
    public static Blob testBlob() {
        return blobOf(TEST_EMBEDDINGS_PARQUET_FILENAME);
    }

    static File fileOf(String name) {
        try {
            return new File(classLoader.getResource(name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot resolve " + name + " from the test classpath", e);
        }
    }

     static Blob blobOf(String name) {
        return Blobs.createBlob(fileOf(name));
    }

    record ExpectedEmbedding(String sourceId, String type, String text, List<Double> vector,
            LocationExpectation location) {

        String uuid() {
            // Mirror ParquetEmbeddingsParser#toScopedId logic for non-UUID raw ids
            String composite = type + ":" + sourceId;
            return UUID.nameUUIDFromBytes(composite.getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    record LocationExpectation(int page, int paragraph, int left, int top, int right, int bottom, double start,
            double end, String sheet, int row, int column) {

        static LocationExpectation standard() {
            return new LocationExpectation(1, 1, 100, 200, 300, 240, 12.5, 17.25, "Sheet1", 1, 1);
        }

        void assertMatches(LocationModel location) {
            assertNotNull(location, "Embedding location should be present");

            LocationModel.TextLocation textLocation = location.text();
            assertNotNull(textLocation, "Text location should be populated");
            assertEquals(page, textLocation.page());
            assertEquals(paragraph, textLocation.paragraph());

            LocationModel.ImageLocation imageLocation = location.position();
            assertNotNull(imageLocation, "Image location should be populated");
            assertEquals(left, imageLocation.left());
            assertEquals(top, imageLocation.top());
            assertEquals(right, imageLocation.right());
            assertEquals(bottom, imageLocation.bottom());

            LocationModel.VideoLocation videoLocation = location.timestamp();
            assertNotNull(videoLocation, "Video location should be populated");
            assertEquals(start, videoLocation.start());
            assertEquals(end, videoLocation.end());

            LocationModel.SpreadSheetLocation spreadsheetLocation = location.spreadsheet();
            assertNotNull(spreadsheetLocation, "Spreadsheet location should be populated");
            assertEquals(sheet, spreadsheetLocation.sheet());
            assertEquals(row, spreadsheetLocation.row());
            assertEquals(column, spreadsheetLocation.column());
        }
    }
}
