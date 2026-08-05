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
package org.nuxeo.ecm.core.model;

import static org.nuxeo.common.utils.IdUtils.isValidUUID;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record EmbeddingModel(String id, //
                             String repository, //
                             String docId, //
                             String type, //
                             List<Double> vector, //
                             String text, //
                             LocationModel location,
                             String blobRef) {
    public static final int VECTOR_SIZE = 1024;

    public static final int MAX_STRING_LENGTH = 256;

    public EmbeddingModel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(docId, "docId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(vector, "vector");

        requireValidID(id);

        if (vector.size() > VECTOR_SIZE) {
            throw new IllegalArgumentException("Vector size must be %d or less".formatted(VECTOR_SIZE));
        }

        if (vector.size() < VECTOR_SIZE) {
            List<Double> paddedVector = new ArrayList<>(vector);
            for (int i = vector.size(); i < VECTOR_SIZE; i++) {
                paddedVector.add(0.0); // pad with zeros if less than VECTOR_SIZE
            }
            vector = List.copyOf(paddedVector);
        } else {
            vector = List.copyOf(vector);
        }

        requireMaxLength(type, "type");
        requireMaxLength(blobRef, "blobRef");
        // text and location are not required.
    }

    private void requireValidID(String id) {
        if (!isValidUUID(id)) {
            throw new IllegalArgumentException("Invalid ID format: %s".formatted(id));
        }
    }

    private void requireMaxLength(Object field, String fieldName) {
        if (field instanceof String str && str.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("Field %s must be %d characters or less".formatted(fieldName, MAX_STRING_LENGTH));
        }
    }
}
