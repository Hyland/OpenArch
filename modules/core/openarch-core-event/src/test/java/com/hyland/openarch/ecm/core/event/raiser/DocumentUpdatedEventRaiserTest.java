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
package com.hyland.openarch.ecm.core.event.raiser;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.runtime.api.Framework;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UPDATED;

class DocumentUpdatedEventRaiserTest {

    private final DocumentUpdatedEventRaiser raiser = new DocumentUpdatedEventRaiser();

    @Test
    void raiseDelegatesToSenderWithPropsContext() {
        CoreSession session = mock(CoreSession.class);
        when(session.getRepositoryName()).thenReturn("default");
        RepositoryPrincipal principal = mock(RepositoryPrincipal.class);

        Map<String, Serializable> docProps = Map.of("key1", "value1");
        Map<String, Map<String, Serializable>> props = Map.of("doc-1", docProps);
        DocumentEventRaiserContext context = new DocumentEventRaiserContext(session, principal, props, null,
                Set.of("doc-1"), null, null, null);

        DocumentEventSenderService sender = mock(DocumentEventSenderService.class);
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(DocumentEventSenderService.class)).thenReturn(sender);

            raiser.raise(context);

            verify(sender).sendDocumentEvents(eq("default"), eq(DOCUMENT_UPDATED), eq(Set.of("doc-1")),
                    eq(principal), eq(props));
        }
    }

    @Test
    void raiseWithoutPropertiesStillDelegatesToSender() {
        CoreSession session = mock(CoreSession.class);
        when(session.getRepositoryName()).thenReturn("default");

        DocumentEventRaiserContext context = new DocumentEventRaiserContext(session, null, null, null,
                Set.of("doc-2"), null, null, null);

        DocumentEventSenderService sender = mock(DocumentEventSenderService.class);
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(DocumentEventSenderService.class)).thenReturn(sender);

            raiser.raise(context);

            verify(sender).sendDocumentEvents(eq("default"), eq(DOCUMENT_UPDATED), eq(Set.of("doc-2")),
                    any(), any());
        }
    }

    @Test
    void raiseWithNullSessionDoesNotDelegate() {
        DocumentEventRaiserContext context = new DocumentEventRaiserContext(null, null, null, null,
                Set.of("doc-3"), null, null, null);

        DocumentEventSenderService sender = mock(DocumentEventSenderService.class);
        try (MockedStatic<Framework> framework = mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(DocumentEventSenderService.class)).thenReturn(sender);

            raiser.raise(context);

            verify(sender, never()).sendDocumentEvents(any(), any(), any(), any(), any());
        }
    }
}
