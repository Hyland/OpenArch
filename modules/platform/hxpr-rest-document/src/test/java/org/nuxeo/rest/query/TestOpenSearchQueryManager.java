/*
 * (C) Copyright 2026 Hyland (http://www.hyland.com/) and others.
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
package org.nuxeo.rest.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_LOCATION;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TEXT;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TYPE;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_VECTOR;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nuxeo.ecm.core.model.LocationModel;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.rest.embedding.EmbeddingRestDTO;
import org.opensearch.client.opensearch.core.search.Hit;

/**
 * Unit tests for {@link OpenSearchQueryManager}.
 */
@ExtendWith(MockitoExtension.class)
class TestOpenSearchQueryManager {

    private static final String HIT_ID = "emb-id";

    private static final double HIT_SCORE = 0.95;

    @Mock
    Hit<DocumentDTO> hit;

    OpenSearchQueryManager manager;

    @BeforeEach
    void setUp() {
        manager = new OpenSearchQueryManager(null, null, null);
        when(hit.id()).thenReturn(HIT_ID);
        when(hit.score()).thenReturn(HIT_SCORE);
    }

    @Test
    void embeddingFromHitMapsAllFields() {
        when(hit.source()).thenReturn(new DocumentDTO(Map.of(
                SYS_ID, "doc-123",
                EMBEDDING_PROPERTY_TYPE, "test-type",
                EMBEDDING_PROPERTY_VECTOR, List.of(0.1, 0.2),
                EMBEDDING_PROPERTY_TEXT, "sample text",
                EMBEDDING_PROPERTY_LOCATION, Map.of("text", Map.of("page", 3, "paragraph", 5)))));

        EmbeddingRestDTO dto = manager.embeddingFromHit(hit);

        assertEquals(new EmbeddingRestDTO(HIT_ID, "doc-123", "test-type",
                List.of(0.1, 0.2), "sample text", null, new LocationModel(3, 5), HIT_SCORE), dto);
    }

    @Test
    void nullLocationYieldsNullDtoLocation() {
        // Location left null (omitted from map)
        when(hit.source()).thenReturn(new DocumentDTO(Map.of(
                SYS_ID, "doc-123",
                EMBEDDING_PROPERTY_TYPE, "test-type",
                EMBEDDING_PROPERTY_VECTOR, List.of(0.1, 0.2),
                EMBEDDING_PROPERTY_TEXT, "sample text")));

        EmbeddingRestDTO dto = manager.embeddingFromHit(hit);

        assertNull(dto.location(), "Location should be null when omitted from hit properties");
    }
}
