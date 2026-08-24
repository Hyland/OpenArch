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

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSEMB_RESIDENCY_CLOUD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSEMB_RESIDENCY_ON_PREM;
import static org.nuxeo.ecm.core.model.EmbeddingModel.VECTOR_SIZE;

import java.io.StringWriter;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.model.EmbeddingModel;
import org.nuxeo.ecm.core.model.LocationModel;
import org.nuxeo.opensearch.test.RepositoryOpenSearchFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.opensearch.client.json.jackson.JacksonJsonProvider;
import org.skyscreamer.jsonassert.JSONAssert;

import jakarta.inject.Inject;
import jakarta.json.stream.JsonGenerator;

@ExtendWith(FeaturesExtension.class)
@Features(RepositoryOpenSearchFeature.class)
public class EmbeddingOSDTOTest {

    @Inject
    CoreSession session;

    /**
     * Creates a real DocumentModel for testing embedding serialization.
     * Uses real Nuxeo infrastructure just like in production.
     */
    private DocumentModel createTestParentDoc() {
        DocumentModel doc = session.createDocumentModel("/", "test-doc-" + UUID.randomUUID(), "SysFile");
        doc.create();
        session.save();
        return doc;
    }
    @Test
    public void testSerializeWithTextLocation() throws JSONException {
        StringWriter writer = new StringWriter();
        JsonGenerator jg = JacksonJsonProvider.provider().createGenerator(writer);
        String embeddingId = UUID.randomUUID().toString();

        List<Double> vector = IntStream.range(0, VECTOR_SIZE).mapToObj(Double::valueOf).toList();
        EmbeddingModel embedding = new EmbeddingModel(embeddingId, "repository", "docId", "type", vector, "text",
                new LocationModel(1, 1), null);
        EmbeddingOSDTO embeddingOSDTO = new EmbeddingOSDTO(embedding, createTestParentDoc(), null);

        // Call the method under test.
        embeddingOSDTO.serialize(jg, null);

        jg.flush();
        String vectorAsString = vector.stream().map(Object::toString).collect(joining(", "));
        // Verify embedding-specific fields are serialized correctly
        // (denormalized parent fields are also included but not strictly verified in this unit test)
        String expected = """
                    {
                      "sysembed_id": "%s",
                      "sysembed_type": "type",
                      "sysembed_vector": [%s],
                      "sysembed_text": "text",
                      "sysembed_location": {
                        "text": {
                          "page": 1,
                          "paragraph": 1
                        }
                      }
                    }
                """.formatted(embeddingId, vectorAsString);
        JSONAssert.assertEquals("Unexpected JSON received", expected, writer.toString(), false);
    }

    @Test
    public void testSerializeWithImageLocation() throws JSONException {
        StringWriter writer = new StringWriter();
        JsonGenerator jg = JacksonJsonProvider.provider().createGenerator(writer);
        String embeddingId = UUID.randomUUID().toString();

        List<Double> vector = IntStream.range(0, VECTOR_SIZE).mapToObj(Double::valueOf).toList();
        EmbeddingModel embedding = new EmbeddingModel(embeddingId, "repository", "docId", "type", vector, "text",
                new LocationModel(10, 20, 100, 200), null);
        EmbeddingOSDTO embeddingOSDTO = new EmbeddingOSDTO(embedding, createTestParentDoc(), null);

        embeddingOSDTO.serialize(jg, null);
        jg.flush();

        String vectorAsString = vector.stream().map(Object::toString).collect(joining(", "));
        String expected = """
                    {
                      "sysembed_id": "%s",
                      "sysembed_type": "type",
                      "sysembed_vector": [%s],
                      "sysembed_text": "text",
                      "sysembed_location": {
                        "position": {
                          "top": 20,
                          "left": 10,
                          "right": 100,
                          "bottom": 200
                        }
                      }
                    }
                """.formatted(embeddingId, vectorAsString);
        JSONAssert.assertEquals("Unexpected JSON for image location", expected, writer.toString(), false);
    }

    @Test
    public void testSerializeWithVideoLocation() throws JSONException {
        StringWriter writer = new StringWriter();
        JsonGenerator jg = JacksonJsonProvider.provider().createGenerator(writer);
        String embeddingId = UUID.randomUUID().toString();

        List<Double> vector = IntStream.range(0, VECTOR_SIZE).mapToObj(Double::valueOf).toList();
        EmbeddingModel embedding = new EmbeddingModel(embeddingId, "repository", "docId", "type", vector, "text",
                new LocationModel(15.5, 30.75), null);
        EmbeddingOSDTO embeddingOSDTO = new EmbeddingOSDTO(embedding, createTestParentDoc(), null);

        embeddingOSDTO.serialize(jg, null);
        jg.flush();

        String vectorAsString = vector.stream().map(Object::toString).collect(joining(", "));
        String expected = """
                    {
                      "sysembed_id": "%s",
                      "sysembed_type": "type",
                      "sysembed_vector": [%s],
                      "sysembed_text": "text",
                      "sysembed_location": {
                        "timestamp": {
                          "start": 15.5,
                          "end": 30.75
                        }
                      }
                    }
                """.formatted(embeddingId, vectorAsString);
        JSONAssert.assertEquals("Unexpected JSON for video location", expected, writer.toString(), false);
    }

    @Test
    public void testSerializeWithSpreadSheetLocation() throws JSONException {
        StringWriter writer = new StringWriter();
        JsonGenerator jg = JacksonJsonProvider.provider().createGenerator(writer);
        String embeddingId = UUID.randomUUID().toString();

        List<Double> vector = IntStream.range(0, VECTOR_SIZE).mapToObj(Double::valueOf).toList();
        EmbeddingModel embedding = new EmbeddingModel(embeddingId, "repository", "docId", "type", vector, "text",
                new LocationModel("Sheet1", 5, 3), null);
        EmbeddingOSDTO embeddingOSDTO = new EmbeddingOSDTO(embedding, createTestParentDoc(), null);

        embeddingOSDTO.serialize(jg, null);
        jg.flush();

        String vectorAsString = vector.stream().map(Object::toString).collect(joining(", "));
        String expected = """
                    {
                      "sysembed_id": "%s",
                      "sysembed_type": "type",
                      "sysembed_vector": [%s],
                      "sysembed_text": "text",
                      "sysembed_location": {
                        "spreadsheet": {
                          "sheet": "Sheet1",
                          "row": 5,
                          "column": 3
                        }
                      }
                    }
                """.formatted(embeddingId, vectorAsString);
        JSONAssert.assertEquals("Unexpected JSON for spreadsheet location", expected, writer.toString(), false);
    }

    @Test
    public void testSerializeWithNullLocation() throws JSONException {
        StringWriter writer = new StringWriter();
        JsonGenerator jg = JacksonJsonProvider.provider().createGenerator(writer);
        String embeddingId = UUID.randomUUID().toString();

        List<Double> vector = IntStream.range(0, VECTOR_SIZE).mapToObj(Double::valueOf).toList();
        EmbeddingModel embedding = new EmbeddingModel(embeddingId, "repository", "docId", "type", vector, "text", null, null);
        EmbeddingOSDTO embeddingOSDTO = new EmbeddingOSDTO(embedding, createTestParentDoc(), null);

        embeddingOSDTO.serialize(jg, null);
        jg.flush();

        String vectorAsString = vector.stream().map(Object::toString).collect(joining(", "));
        String expected = """
                    {
                      "sysembed_id": "%s",
                      "sysembed_type": "type",
                      "sysembed_vector": [%s],
                      "sysembed_text": "text"
                    }
                """.formatted(embeddingId, vectorAsString);
        JSONAssert.assertEquals("Unexpected JSON for null location", expected, writer.toString(), false);
    }

    @Test
    public void testSerializeWithBlobRef() throws JSONException {
        StringWriter writer = new StringWriter();
        JsonGenerator jg = JacksonJsonProvider.provider().createGenerator(writer);
        String embeddingId = UUID.randomUUID().toString();

        List<Double> vector = IntStream.range(0, VECTOR_SIZE).mapToObj(Double::valueOf).toList();

        String blobRef = "some-blob-reference-id";

        EmbeddingModel embedding = new EmbeddingModel(
                embeddingId,
                "repository",
                "docId",
                "type",
                vector,
                "text",
                null,          // no location
                blobRef        // blobref set
        );

        EmbeddingOSDTO embeddingOSDTO = new EmbeddingOSDTO(embedding, createTestParentDoc(), null);

        embeddingOSDTO.serialize(jg, null);
        jg.flush();

        String vectorAsString = vector.stream().map(Object::toString).collect(joining(", "));
        String expected = """
                {
                  "sysembed_id": "%s",
                  "sysembed_type": "type",
                  "sysembed_vector": [%s],
                  "sysembed_text": "text",
                  "sysembed_blobref": "some-blob-reference-id"
                }
            """.formatted(embeddingId, vectorAsString);

        JSONAssert.assertEquals("Unexpected JSON for blobref", expected, writer.toString(), false);
    }

    @Test
    public void testSerializeWithNullBlobRef() throws JSONException {
        StringWriter writer = new StringWriter();
        JsonGenerator jg = JacksonJsonProvider.provider().createGenerator(writer);
        String embeddingId = UUID.randomUUID().toString();

        List<Double> vector = IntStream.range(0, VECTOR_SIZE).mapToObj(Double::valueOf).toList();

        // blobref explicitly null
        EmbeddingModel embedding = new EmbeddingModel(
                embeddingId,
                "repository",
                "docId",
                "type",
                vector,
                "text",
                new LocationModel(1, 6),  // any location, just to match realistic output
                null                     // blobref = null
        );

        EmbeddingOSDTO embeddingOSDTO = new EmbeddingOSDTO(embedding, createTestParentDoc(), null);

        embeddingOSDTO.serialize(jg, null);
        jg.flush();

        String vectorAsString = vector.stream().map(Object::toString).collect(joining(", "));
        String expected = """
                {
                  "sysembed_id": "%s",
                  "sysembed_type": "type",
                  "sysembed_vector": [%s],
                  "sysembed_text": "text",
                  "sysembed_location": {
                    "text": {
                      "page": 1,
                      "paragraph": 6
                    }
                  }
                }
            """.formatted(embeddingId, vectorAsString);

        JSONAssert.assertEquals("Unexpected JSON when blobref is null", expected, writer.toString(), false);
    }

    @Test
    public void testSerializeOnPremOmitsChunkText() {
        StringWriter writer = new StringWriter();
        JsonGenerator jg = JacksonJsonProvider.provider().createGenerator(writer);
        String embeddingId = UUID.randomUUID().toString();

        List<Double> vector = IntStream.range(0, VECTOR_SIZE).mapToObj(Double::valueOf).toList();
        EmbeddingModel embedding = new EmbeddingModel(embeddingId, "repository", "docId", "type", vector, "secret text",
                new LocationModel(1, 1), null);
        EmbeddingOSDTO embeddingOSDTO = new EmbeddingOSDTO(embedding, createTestParentDoc(), SYSEMB_RESIDENCY_ON_PREM);

        embeddingOSDTO.serialize(jg, null);
        jg.flush();

        String actual = writer.toString();
        assertFalse(actual.contains("sysembed_text"), "onPrem _source must not carry sysembed_text: " + actual);
        assertFalse(actual.contains("secret text"), "onPrem _source must not leak chunk text: " + actual);
        assertTrue(actual.contains("sysembed_vector"), "onPrem _source must still carry the vector");
        assertTrue(actual.contains(embeddingId), "onPrem _source must still carry the embedding id");
    }

    @Test
    public void testSerializeCloudIncludesChunkText() {
        StringWriter writer = new StringWriter();
        JsonGenerator jg = JacksonJsonProvider.provider().createGenerator(writer);
        String embeddingId = UUID.randomUUID().toString();

        List<Double> vector = IntStream.range(0, VECTOR_SIZE).mapToObj(Double::valueOf).toList();
        EmbeddingModel embedding = new EmbeddingModel(embeddingId, "repository", "docId", "type", vector, "text",
                new LocationModel(1, 1), null);
        EmbeddingOSDTO embeddingOSDTO = new EmbeddingOSDTO(embedding, createTestParentDoc(), SYSEMB_RESIDENCY_CLOUD);

        embeddingOSDTO.serialize(jg, null);
        jg.flush();

        assertTrue(writer.toString().contains("\"sysembed_text\""),
                "cloud _source must carry sysembed_text: " + writer);
    }
}
