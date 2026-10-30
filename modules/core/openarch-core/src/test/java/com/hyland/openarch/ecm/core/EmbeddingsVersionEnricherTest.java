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
package com.hyland.openarch.ecm.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_CHUNKS;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_CHUNKS_VERSION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSEMB_ON_PREM_PROCESSING_SUCCESS;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_EMBEDDINGS;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.hyland.openarch.ecm.core.api.DocumentEnrichmentContext;
import com.hyland.openarch.ecm.core.api.UpdateCondition;
import com.hyland.openarch.ecm.core.model.InternalDocument;
import com.hyland.openarch.ecm.core.schema.DocumentType;

/**
 * Mock-based unit tests for {@link EmbeddingsVersionEnricher}.
 */
public class EmbeddingsVersionEnricherTest {

    @Mock
    private DocumentEnrichmentContext context;

    @Mock
    private InternalDocument document;

    @Mock
    private DocumentType type;

    private EmbeddingsVersionEnricher enricher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        enricher = new EmbeddingsVersionEnricher();
        when(context.getDocument()).thenReturn(document);
        when(document.getType()).thenReturn(type);
        when(type.getName()).thenReturn(SYS_EMBEDDINGS);
    }

    private void updatedFields(Map<String, Object> fields) {
        when(context.getUpdatedFields()).thenReturn(fields);
    }

    @Test
    void onCreate_setsInitialVersion() {
        enricher.onCreate(context);

        verify(document).setPropertyValue(SYSEMB_CHUNKS_VERSION, 1L);
    }

    @Test
    void onCreate_ignoresOtherTypes() {
        when(type.getName()).thenReturn("File");

        enricher.onCreate(context);

        verify(document, never()).setPropertyValue(eq(SYSEMB_CHUNKS_VERSION), any());
    }

    @Test
    void onUpdate_chunksUpdated_incrementsExistingVersion() {
        updatedFields(Map.of(SYSEMB_CHUNKS, "new"));
        when(document.getPropertyValue(SYSEMB_CHUNKS_VERSION)).thenReturn(3L);

        enricher.onUpdate(context);

        verify(document).addUpdateCondition(eq(UpdateCondition.hxql(SYSEMB_CHUNKS_VERSION + " = 3")), any());
        verify(document).setPropertyValue(SYSEMB_CHUNKS_VERSION, 4L);
    }

    @Test
    void onUpdate_chunksUpdatedWithoutExistingVersion_setsInitialVersion() {
        updatedFields(Map.of(SYSEMB_CHUNKS, "new"));
        when(document.getPropertyValue(SYSEMB_CHUNKS_VERSION)).thenReturn(null);

        enricher.onUpdate(context);

        verify(document).addUpdateCondition(eq(UpdateCondition.hxql(SYSEMB_CHUNKS_VERSION + " IS NULL")), any());
        verify(document).setPropertyValue(SYSEMB_CHUNKS_VERSION, 1L);
    }

    @Test
    void onUpdate_chunksCleared_incrementsVersion() {
        // clearing the on-prem flag is recorded as a null value, which is not a Boolean
        Map<String, Object> fields = new HashMap<>();
        fields.put(SYSEMB_CHUNKS, null);
        fields.put(SYSEMB_ON_PREM_PROCESSING_SUCCESS, null);
        updatedFields(fields);
        when(document.getPropertyValue(SYSEMB_CHUNKS_VERSION)).thenReturn(2L);

        enricher.onUpdate(context);

        verify(document).addUpdateCondition(eq(UpdateCondition.hxql(SYSEMB_CHUNKS_VERSION + " = 2")), any());
        verify(document).setPropertyValue(SYSEMB_CHUNKS_VERSION, 3L);
    }

    @Test
    void onUpdate_chunksAndProcessingFlagUpdated_doesNotIncrement() {
        Map<String, Object> fields = new HashMap<>();
        fields.put(SYSEMB_CHUNKS, "new");
        fields.put(SYSEMB_ON_PREM_PROCESSING_SUCCESS, true);
        updatedFields(fields);

        enricher.onUpdate(context);

        verify(document, never()).addUpdateCondition(any(), any());
        verify(document, never()).setPropertyValue(eq(SYSEMB_CHUNKS_VERSION), any());
    }

    @Test
    void onUpdate_versionExplicitlyUpdated_doesNotIncrement() {
        updatedFields(Map.of(SYSEMB_CHUNKS, "new", SYSEMB_CHUNKS_VERSION, 7L));

        enricher.onUpdate(context);

        verify(document, never()).addUpdateCondition(any(), any());
        verify(document, never()).setPropertyValue(eq(SYSEMB_CHUNKS_VERSION), any());
    }

    @Test
    void onUpdate_chunksNotUpdated_doesNotIncrement() {
        updatedFields(Map.of(SYSEMB_ON_PREM_PROCESSING_SUCCESS, false));

        enricher.onUpdate(context);

        verify(document, never()).addUpdateCondition(any(), any());
        verify(document, never()).setPropertyValue(eq(SYSEMB_CHUNKS_VERSION), any());
    }

    @Test
    void onUpdate_ignoresOtherTypes() {
        when(type.getName()).thenReturn("File");
        updatedFields(Map.of(SYSEMB_CHUNKS, "new"));

        enricher.onUpdate(context);

        verify(document, never()).addUpdateCondition(any(), any());
        verify(document, never()).setPropertyValue(eq(SYSEMB_CHUNKS_VERSION), any());
    }
}
