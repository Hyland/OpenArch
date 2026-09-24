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
package com.hyland.openarch.ecm.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EmbeddingModelTest {

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static List<Double> vectorOfSize(int size) {
        List<Double> vector = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            vector.add((double) i);
        }
        return vector;
    }

    @Test
    void shouldBuildWithMinimalRequiredFields() {
        EmbeddingModel model = new EmbeddingModel(newId(), "default", "docId", "type", vectorOfSize(10), null, null,
                null);

        assertEquals("default", model.repository());
        assertEquals("docId", model.docId());
        assertEquals("type", model.type());
        assertEquals(EmbeddingModel.VECTOR_SIZE, model.vector().size());
        assertNull(model.text());
        assertNull(model.location());
        assertNull(model.blobRef());
    }

    @Test
    void shouldPadShorterVectorWithZeros() {
        EmbeddingModel model = new EmbeddingModel(newId(), "r", "d", "t", vectorOfSize(3), null, null, null);

        assertEquals(EmbeddingModel.VECTOR_SIZE, model.vector().size());
        // original values are retained
        assertEquals(0.0, model.vector().get(0));
        assertEquals(1.0, model.vector().get(1));
        assertEquals(2.0, model.vector().get(2));
        // the rest is padded with zeros
        assertEquals(0.0, model.vector().get(3));
        assertEquals(0.0, model.vector().get(EmbeddingModel.VECTOR_SIZE - 1));
    }

    @Test
    void shouldAcceptVectorOfExactSize() {
        EmbeddingModel model = new EmbeddingModel(newId(), "r", "d", "t", vectorOfSize(EmbeddingModel.VECTOR_SIZE),
                null, null, null);

        assertEquals(EmbeddingModel.VECTOR_SIZE, model.vector().size());
    }

    @Test
    void shouldExposeAnImmutableVector() {
        EmbeddingModel model = new EmbeddingModel(newId(), "r", "d", "t", vectorOfSize(5), null, null, null);

        List<Double> vector = model.vector();
        assertThrows(UnsupportedOperationException.class, () -> vector.set(0, 9.9));
    }

    @Test
    void shouldRejectVectorLargerThanMax() {
        String id = newId();
        List<Double> tooBig = vectorOfSize(EmbeddingModel.VECTOR_SIZE + 1);

        assertThrows(IllegalArgumentException.class,
                () -> new EmbeddingModel(id, "r", "d", "t", tooBig, null, null, null));
    }

    @Test
    void shouldRejectInvalidId() {
        List<Double> vector = vectorOfSize(1);

        assertThrows(IllegalArgumentException.class,
                () -> new EmbeddingModel("not-a-uuid", "r", "d", "t", vector, null, null, null));
    }

    @Test
    void shouldRejectNullRequiredFields() {
        String id = newId();
        List<Double> vector = vectorOfSize(1);

        assertThrows(NullPointerException.class,
                () -> new EmbeddingModel(null, "r", "d", "t", vector, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new EmbeddingModel(id, null, "d", "t", vector, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new EmbeddingModel(id, "r", null, "t", vector, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new EmbeddingModel(id, "r", "d", null, vector, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new EmbeddingModel(id, "r", "d", "t", null, null, null, null));
    }

    @Test
    void shouldRejectTypeExceedingMaxLength() {
        String id = newId();
        String longType = "a".repeat(EmbeddingModel.MAX_STRING_LENGTH + 1);
        List<Double> vector = vectorOfSize(1);

        assertThrows(IllegalArgumentException.class,
                () -> new EmbeddingModel(id, "r", "d", longType, vector, null, null, null));
    }

    @Test
    void shouldRejectBlobRefExceedingMaxLength() {
        String id = newId();
        String longBlobRef = "b".repeat(EmbeddingModel.MAX_STRING_LENGTH + 1);
        List<Double> vector = vectorOfSize(1);

        assertThrows(IllegalArgumentException.class,
                () -> new EmbeddingModel(id, "r", "d", "t", vector, null, null, longBlobRef));
    }

    @Test
    void shouldAcceptOptionalFieldsAndValuesAtMaxLength() {
        String id = newId();
        String maxType = "a".repeat(EmbeddingModel.MAX_STRING_LENGTH);
        String maxBlobRef = "b".repeat(EmbeddingModel.MAX_STRING_LENGTH);

        EmbeddingModel model = new EmbeddingModel(id, "r", "d", maxType, vectorOfSize(1), "some text", null, maxBlobRef);

        assertEquals(maxType, model.type());
        assertEquals("some text", model.text());
        assertEquals(maxBlobRef, model.blobRef());
    }
}
