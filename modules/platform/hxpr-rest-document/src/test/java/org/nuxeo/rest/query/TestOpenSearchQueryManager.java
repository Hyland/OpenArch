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
package org.nuxeo.rest.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.CIN_SOURCE_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_RESIDENCY;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_RESIDENCY_CLOUD;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_RESIDENCY_ON_PREM;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_LOCATION;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TEXT;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_TYPE;
import static com.hyland.openarch.ecm.core.api.EmbeddingConstants.EMBEDDING_PROPERTY_VECTOR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.EmbeddingChunkTextFetcher;
import com.hyland.openarch.ecm.core.api.impl.DocumentModelListImpl;
import com.hyland.openarch.ecm.core.model.LocationModel;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.rest.embedding.EmbeddingRestDTO;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit tests for {@link OpenSearchQueryManager}.
 */
@ExtendWith(MockitoExtension.class)
class TestOpenSearchQueryManager {

    private static final String HIT_ID = "emb-id";

    private static final double HIT_SCORE = 0.95;

    @Mock
    Hit<DocumentDTO> hit;

    @Mock
    HitsMetadata<DocumentDTO> hits;

    @Mock
    CoreSession session;

    OpenSearchQueryManager manager;

    @BeforeEach
    void setUp() {
        manager = new OpenSearchQueryManager(null, null, null, null);
    }

    @Test
    void embeddingFromHitMapsAllFields() {
        when(hit.id()).thenReturn(HIT_ID);
        when(hit.score()).thenReturn(HIT_SCORE);
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
        when(hit.id()).thenReturn(HIT_ID);
        when(hit.score()).thenReturn(HIT_SCORE);
        when(hit.source()).thenReturn(new DocumentDTO(Map.of(
                SYS_ID, "doc-123",
                EMBEDDING_PROPERTY_TYPE, "test-type",
                EMBEDDING_PROPERTY_VECTOR, List.of(0.1, 0.2),
                EMBEDDING_PROPERTY_TEXT, "sample text")));

        EmbeddingRestDTO dto = manager.embeddingFromHit(hit);

        assertNull(dto.location(), "Location should be null when omitted from hit properties");
    }

    @Test
    void cloudHitsPassThroughWithSourceText() {
        Hit<DocumentDTO> cloudHit = mockHit("cloud-1", "doc-1", "cloud from source", SYSEMB_RESIDENCY_CLOUD);
        when(hits.hits()).thenReturn(List.of(cloudHit));
        when(session.getDocuments(any())).thenReturn(new DocumentModelListImpl());

        List<EmbeddingRestDTO> result = manager.getEmbeddingRestDTOs(hits, session);

        assertEquals(1, result.size());
        assertEquals("cloud-1", result.get(0).id());
        assertEquals("cloud from source", result.get(0).text());
    }

    @Test
    void onPremHitsAreOmittedWhenNoFetcherRegistered() {
        Hit<DocumentDTO> onPremHit = mockHit("op-1", "doc-1", null, SYSEMB_RESIDENCY_ON_PREM);
        when(hits.hits()).thenReturn(List.of(onPremHit));

        List<EmbeddingRestDTO> result = manager.getEmbeddingRestDTOs(hits, session);

        assertTrue(result.isEmpty());
    }

    @Test
    void onPremHitsAreEnrichedWithFetchedText() {
        manager = managerWithFetcher(Map.of("op-1", "fetched text"));
        Hit<DocumentDTO> onPremHit = mockHit("op-1", "doc-1", null, SYSEMB_RESIDENCY_ON_PREM);
        when(hits.hits()).thenReturn(List.of(onPremHit));
        when(session.getDocuments(any())).thenReturn(new DocumentModelListImpl());

        List<EmbeddingRestDTO> result = manager.getEmbeddingRestDTOs(hits, session);

        assertEquals(1, result.size());
        assertEquals("fetched text", result.get(0).text());
    }

    @Test
    void onPremHitsWithoutFetchedTextAreOmitted() {
        manager = managerWithFetcher(Map.of());
        Hit<DocumentDTO> onPremHit = mockHit("op-1", "doc-1", null, SYSEMB_RESIDENCY_ON_PREM);
        when(hits.hits()).thenReturn(List.of(onPremHit));

        List<EmbeddingRestDTO> result = manager.getEmbeddingRestDTOs(hits, session);

        assertTrue(result.isEmpty());
    }

    @Test
    void mixedResidencyKeepsCloudEnrichesOnPremDropsMissing() {
        manager = managerWithFetcher(Map.of("op-present", "resolved"));
        Hit<DocumentDTO> cloudHit = mockHit("cloud-1", "doc-c", "cloud text", SYSEMB_RESIDENCY_CLOUD);
        Hit<DocumentDTO> onPremPresent = mockHit("op-present", "doc-p", null, SYSEMB_RESIDENCY_ON_PREM);
        Hit<DocumentDTO> onPremMissing = mockHit("op-missing", "doc-m", null, SYSEMB_RESIDENCY_ON_PREM);
        when(hits.hits()).thenReturn(List.of(cloudHit, onPremPresent, onPremMissing));
        when(session.getDocuments(any())).thenReturn(new DocumentModelListImpl());

        List<EmbeddingRestDTO> result = manager.getEmbeddingRestDTOs(hits, session);

        assertEquals(2, result.size());
        assertEquals("cloud-1", result.get(0).id());
        assertEquals("cloud text", result.get(0).text());
        assertEquals("op-present", result.get(1).id());
        assertEquals("resolved", result.get(1).text());
    }

    @Test
    void multipleSourcesEachInvokeFetcherSeparately() {
        Map<String, Map<String, String>> textsBySource = new HashMap<>();
        textsBySource.put("sys-a", Map.of("a-1", "text-a"));
        textsBySource.put("sys-b", Map.of("b-1", "text-b"));
        List<String> invokedSources = new ArrayList<>();
        EmbeddingChunkTextFetcher fetcher = (sourceId, ids) -> {
            invokedSources.add(sourceId);
            return textsBySource.getOrDefault(sourceId, Map.of());
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<EmbeddingChunkTextFetcher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(fetcher);
        manager = new OpenSearchQueryManager(null, null, null, provider);

        Hit<DocumentDTO> hitA = mockHit("a-1", "doc-a", null, SYSEMB_RESIDENCY_ON_PREM, "sys-a");
        Hit<DocumentDTO> hitB = mockHit("b-1", "doc-b", null, SYSEMB_RESIDENCY_ON_PREM, "sys-b");
        when(hits.hits()).thenReturn(List.of(hitA, hitB));
        when(session.getDocuments(any())).thenReturn(new DocumentModelListImpl());

        List<EmbeddingRestDTO> result = manager.getEmbeddingRestDTOs(hits, session);

        assertEquals(2, result.size());
        assertTrue(invokedSources.containsAll(List.of("sys-a", "sys-b")));
        assertEquals(2, invokedSources.size());
    }

    private static final String DEFAULT_SOURCE_ID = "system-1";

    private static Hit<DocumentDTO> mockHit(String id, String docId, String sourceText, String residency) {
        return mockHit(id, docId, sourceText, residency, DEFAULT_SOURCE_ID);
    }

    private static Hit<DocumentDTO> mockHit(String id, String docId, String sourceText, String residency,
            String sourceId) {
        @SuppressWarnings("unchecked")
        Hit<DocumentDTO> h = mock(Hit.class);
        when(h.id()).thenReturn(id);
        when(h.score()).thenReturn(HIT_SCORE);
        Map<String, Object> props = new HashMap<>();
        props.put(SYS_ID, docId);
        props.put(EMBEDDING_PROPERTY_TYPE, "test-type");
        props.put(EMBEDDING_PROPERTY_VECTOR, List.of(0.1, 0.2));
        if (sourceText != null) {
            props.put(EMBEDDING_PROPERTY_TEXT, sourceText);
        }
        props.put(SYSEMB_RESIDENCY, residency);
        if (sourceId != null) {
            props.put(CIN_SOURCE_ID, sourceId);
        }
        when(h.source()).thenReturn(new DocumentDTO(props));
        return h;
    }

    private static OpenSearchQueryManager managerWithFetcher(Map<String, String> texts) {
        EmbeddingChunkTextFetcher fetcher = (sourceId, ids) -> {
            Map<String, String> result = new HashMap<>();
            for (String id : ids) {
                if (texts.containsKey(id)) {
                    result.put(id, texts.get(id));
                }
            }
            return result;
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<EmbeddingChunkTextFetcher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(fetcher);
        return new OpenSearchQueryManager(null, null, null, provider);
    }
}
