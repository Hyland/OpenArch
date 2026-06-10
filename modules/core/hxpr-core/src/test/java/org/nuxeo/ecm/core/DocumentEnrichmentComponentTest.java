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
package org.nuxeo.ecm.core;

import static org.mockito.Mockito.inOrder;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.nuxeo.ecm.core.api.DocumentEnricher;
import org.nuxeo.ecm.core.api.DocumentEnrichmentContext;

/**
 * Mock-based unit tests for {@link DocumentEnrichmentComponent}.
 */
public class DocumentEnrichmentComponentTest {

    @Mock
    private DocumentEnricher enricherA;

    @Mock
    private DocumentEnricher enricherB;

    @Mock
    private DocumentEnrichmentContext context;

    private DocumentEnrichmentComponent component;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        component = new DocumentEnrichmentComponent();
    }

    private void registerEnrichers(DocumentEnricher... enrichers) throws ReflectiveOperationException {
        Field field = DocumentEnrichmentComponent.class.getDeclaredField("enrichers");
        field.setAccessible(true);
        field.set(component, List.of(enrichers));
    }

    @Test
    void enrichOnCreate_fansOutToAllEnrichersInRegistrationOrder() throws ReflectiveOperationException {
        registerEnrichers(enricherA, enricherB);

        component.enrichOnCreate(context);

        InOrder inOrder = inOrder(enricherA, enricherB);
        inOrder.verify(enricherA).onCreate(context);
        inOrder.verify(enricherB).onCreate(context);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void enrichOnUpdate_fansOutToAllEnrichersInRegistrationOrder() throws ReflectiveOperationException {
        registerEnrichers(enricherA, enricherB);

        component.enrichOnUpdate(context);

        InOrder inOrder = inOrder(enricherA, enricherB);
        inOrder.verify(enricherA).onUpdate(context);
        inOrder.verify(enricherB).onUpdate(context);
        inOrder.verifyNoMoreInteractions();
    }
}
