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
package org.nuxeo.ecm.core.event.raiser;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import com.hyland.openarch.runtime.api.Framework;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.event.DocumentEventCategories.EVENT_DOCUMENT_CATEGORY;

class DocumentEventSenderComponentTest {

    private static final String REPOSITORY = "default";

    private static final String EVENT_NAME = "documentModified";

    private final DocumentEventSenderComponent component = new DocumentEventSenderComponent();

    @Test
    void sendDocumentEventsFiresEventWithContextPropertiesAndCategory() {
        CoreSession session = mock(CoreSession.class);
        DocumentModel doc = mock(DocumentModel.class);
        when(session.getDocument(new IdRef("doc-1"))).thenReturn(doc);
        NuxeoPrincipal principal = mock(NuxeoPrincipal.class);

        Map<String, Serializable> docProps = Map.of("key1", "value1");
        Map<String, Map<String, Serializable>> contextPropertiesById = Map.of("doc-1", docProps);

        EventService eventService = mock(EventService.class);
        try (MockedStatic<Framework> framework = mockStatic(Framework.class);
                MockedStatic<CoreInstance> coreInstance = mockStatic(CoreInstance.class)) {
            framework.when(() -> Framework.getService(EventService.class)).thenReturn(eventService);
            coreInstance.when(() -> CoreInstance.getCoreSessionSystem(REPOSITORY)).thenReturn(session);

            component.sendDocumentEvents(REPOSITORY, EVENT_NAME, Set.of("doc-1"), principal, contextPropertiesById);

            ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
            verify(eventService).fireEvent(captor.capture());
            Event event = captor.getValue();
            assertThat(event.getName()).isEqualTo(EVENT_NAME);
            DocumentEventContext ctx = (DocumentEventContext) event.getContext();
            assertThat(ctx.getCategory()).isEqualTo(EVENT_DOCUMENT_CATEGORY);
            assertThat(ctx.getProperties()).containsEntry("key1", "value1");
            assertThat(ctx.getSourceDocument()).isSameAs(doc);
        }
    }

    @Test
    void sendDocumentEventsWithNullContextPropertiesStillFiresEvent() {
        CoreSession session = mock(CoreSession.class);
        DocumentModel doc = mock(DocumentModel.class);
        when(session.getDocument(new IdRef("doc-2"))).thenReturn(doc);

        EventService eventService = mock(EventService.class);
        try (MockedStatic<Framework> framework = mockStatic(Framework.class);
                MockedStatic<CoreInstance> coreInstance = mockStatic(CoreInstance.class)) {
            framework.when(() -> Framework.getService(EventService.class)).thenReturn(eventService);
            coreInstance.when(() -> CoreInstance.getCoreSessionSystem(REPOSITORY)).thenReturn(session);

            component.sendDocumentEvents(REPOSITORY, EVENT_NAME, Set.of("doc-2"), null, null);

            verify(eventService).fireEvent(any(Event.class));
        }
    }

    @Test
    void sendDocumentEventsWithMissingContextPropertiesForIdFiresEventWithoutProperties() {
        CoreSession session = mock(CoreSession.class);
        DocumentModel doc = mock(DocumentModel.class);
        when(session.getDocument(new IdRef("doc-3"))).thenReturn(doc);

        Map<String, Map<String, Serializable>> contextPropertiesById = Map.of("other", Map.of("k", "v"));

        EventService eventService = mock(EventService.class);
        try (MockedStatic<Framework> framework = mockStatic(Framework.class);
                MockedStatic<CoreInstance> coreInstance = mockStatic(CoreInstance.class)) {
            framework.when(() -> Framework.getService(EventService.class)).thenReturn(eventService);
            coreInstance.when(() -> CoreInstance.getCoreSessionSystem(REPOSITORY)).thenReturn(session);

            component.sendDocumentEvents(REPOSITORY, EVENT_NAME, Set.of("doc-3"), null, contextPropertiesById);

            ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
            verify(eventService).fireEvent(captor.capture());
            DocumentEventContext ctx = (DocumentEventContext) captor.getValue().getContext();
            assertThat(ctx.getProperties()).doesNotContainKey("k");
        }
    }

    @Test
    void sendDocumentEventsFiresOneEventPerId() {
        CoreSession session = mock(CoreSession.class);
        DocumentModel doc1 = mock(DocumentModel.class);
        DocumentModel doc2 = mock(DocumentModel.class);
        when(session.getDocument(new IdRef("doc-1"))).thenReturn(doc1);
        when(session.getDocument(new IdRef("doc-2"))).thenReturn(doc2);

        EventService eventService = mock(EventService.class);
        try (MockedStatic<Framework> framework = mockStatic(Framework.class);
                MockedStatic<CoreInstance> coreInstance = mockStatic(CoreInstance.class)) {
            framework.when(() -> Framework.getService(EventService.class)).thenReturn(eventService);
            coreInstance.when(() -> CoreInstance.getCoreSessionSystem(REPOSITORY)).thenReturn(session);

            component.sendDocumentEvents(REPOSITORY, EVENT_NAME, Set.of("doc-1", "doc-2"), null, null);

            verify(eventService, times(2)).fireEvent(any(Event.class));
        }
    }

    @Test
    void sendDocumentEventsSkipsDocumentWhenNotFound() {
        CoreSession session = mock(CoreSession.class);
        when(session.getDocument(new IdRef("missing"))).thenThrow(new DocumentNotFoundException("missing"));

        EventService eventService = mock(EventService.class);
        try (MockedStatic<Framework> framework = mockStatic(Framework.class);
                MockedStatic<CoreInstance> coreInstance = mockStatic(CoreInstance.class)) {
            framework.when(() -> Framework.getService(EventService.class)).thenReturn(eventService);
            coreInstance.when(() -> CoreInstance.getCoreSessionSystem(REPOSITORY)).thenReturn(session);

            component.sendDocumentEvents(REPOSITORY, EVENT_NAME, Set.of("missing"), null, null);

            verify(eventService, never()).fireEvent(any(Event.class));
        }
    }

    @Test
    void sendDocumentEventsWithEmptyIdsDoesNothing() {
        EventService eventService = mock(EventService.class);
        try (MockedStatic<Framework> framework = mockStatic(Framework.class);
                MockedStatic<CoreInstance> coreInstance = mockStatic(CoreInstance.class)) {

            component.sendDocumentEvents(REPOSITORY, EVENT_NAME, Set.of(), null, null);

            framework.verifyNoInteractions();
            coreInstance.verifyNoInteractions();
            verify(eventService, never()).fireEvent(any(Event.class));
        }
    }

    @Test
    void sendDocumentEventsFiresValidAndSkipsMissing() {
        CoreSession session = mock(CoreSession.class);
        DocumentModel doc = mock(DocumentModel.class);
        when(session.getDocument(new IdRef("doc-ok"))).thenReturn(doc);
        when(session.getDocument(new IdRef("doc-missing")))
                .thenThrow(new DocumentNotFoundException("doc-missing"));

        EventService eventService = mock(EventService.class);
        try (MockedStatic<Framework> framework = mockStatic(Framework.class);
                MockedStatic<CoreInstance> coreInstance = mockStatic(CoreInstance.class)) {
            framework.when(() -> Framework.getService(EventService.class)).thenReturn(eventService);
            coreInstance.when(() -> CoreInstance.getCoreSessionSystem(REPOSITORY)).thenReturn(session);

            // Use a List-backed ordered set to keep deterministic behaviour
            component.sendDocumentEvents(REPOSITORY, EVENT_NAME, Set.copyOf(List.of("doc-ok", "doc-missing")), null,
                    null);

            verify(eventService, times(1)).fireEvent(any(Event.class));
        }
    }
}
