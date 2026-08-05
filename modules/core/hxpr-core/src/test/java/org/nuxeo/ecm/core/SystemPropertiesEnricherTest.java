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
package org.nuxeo.ecm.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CONTRIBUTORS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LAST_CONTRIBUTOR;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.nuxeo.ecm.core.api.DocumentEnrichmentContext;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.model.InternalDocument;

/**
 * Mock-based unit tests for {@link SystemPropertiesEnricher}.
 */
public class SystemPropertiesEnricherTest {

    private static final String PRINCIPAL_ID = "alice";

    private static final Instant NOW = Instant.parse("2026-06-10T09:00:00Z");

    @Mock
    private DocumentEnrichmentContext context;

    @Mock
    private NuxeoPrincipal principal;

    @Mock
    private InternalDocument document;

    private SystemPropertiesEnricher enricher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        enricher = new SystemPropertiesEnricher();
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getTimestamp()).thenReturn(NOW);
        when(context.getDocument()).thenReturn(document);
        when(principal.getActingId()).thenReturn(PRINCIPAL_ID);
    }

    @Test
    void onCreate_stampsAllSystemFields() {
        when(document.getPropertyValue(SYS_CONTRIBUTORS)).thenReturn(null);

        enricher.onCreate(context);

        verify(document).setPropertyValue(SYS_CREATED, NOW);
        verify(document).setPropertyValue(SYS_CREATOR, PRINCIPAL_ID);
        verify(document).setPropertyValue(SYS_MODIFIED, NOW);
        verify(document).setPropertyValue(SYS_LAST_CONTRIBUTOR, PRINCIPAL_ID);
        verify(document).setPropertyValue(SYS_CONTRIBUTORS, List.of(PRINCIPAL_ID));
    }

    @Test
    void onUpdate_stampsModificationFieldsButNotCreationFields() {
        when(document.getPropertyValue(SYS_CONTRIBUTORS)).thenReturn(null);

        enricher.onUpdate(context);

        verify(document).setPropertyValue(SYS_MODIFIED, NOW);
        verify(document).setPropertyValue(SYS_LAST_CONTRIBUTOR, PRINCIPAL_ID);
        verify(document).setPropertyValue(SYS_CONTRIBUTORS, List.of(PRINCIPAL_ID));
        verify(document, never()).setPropertyValue(eq(SYS_CREATED), any());
        verify(document, never()).setPropertyValue(eq(SYS_CREATOR), any());
    }

    @Test
    void onUpdate_appendsNewContributorPreservingExisting() {
        when(document.getPropertyValue(SYS_CONTRIBUTORS)).thenReturn(List.of("bob"));

        enricher.onUpdate(context);

        verify(document).setPropertyValue(SYS_CONTRIBUTORS, List.of("bob", PRINCIPAL_ID));
    }

    @Test
    void onUpdate_doesNotDuplicateExistingContributor() {
        when(document.getPropertyValue(SYS_CONTRIBUTORS)).thenReturn(List.of(PRINCIPAL_ID));

        enricher.onUpdate(context);

        verify(document, never()).setPropertyValue(eq(SYS_CONTRIBUTORS), any());
    }
}
