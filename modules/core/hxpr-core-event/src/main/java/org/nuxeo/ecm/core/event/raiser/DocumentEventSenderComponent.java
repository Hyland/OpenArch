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

import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.EventImpl;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.DefaultComponent;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import static org.nuxeo.ecm.core.api.event.DocumentEventCategories.EVENT_DOCUMENT_CATEGORY;

/**
 * Nuxeo component implementing {@link DocumentEventSenderService}.
 * <p>
 * The event sending logic was extracted from {@code DBSTransactionState} so that it can be reused
 * and unit-tested independently of the transaction state.
 */
public class DocumentEventSenderComponent extends DefaultComponent implements DocumentEventSenderService {

    @Override
    public void sendDocumentEvents(String repositoryName, String eventName, Set<String> ids, NuxeoPrincipal principal,
            Map<String, Map<String, Serializable>> contextPropertiesById) {
        if (ids.isEmpty()) {
            return;
        }
        // send a core event for each document in the list
        long time = System.currentTimeMillis(); // make sure all events have the same time
        EventService eventService = Framework.getService(EventService.class);
        CoreSession coreSession = CoreInstance.getCoreSessionSystem(repositoryName);
        for (String id : ids) {
            DocumentModel doc;
            try {
                doc = coreSession.getDocument(new IdRef(id));
            } catch (DocumentNotFoundException e) {
                // old doc (UnknownDocumentType), don't send event
                continue;
            }
            DocumentEventContext context = new DocumentEventContext(coreSession, principal, doc);
            context.setCategory(EVENT_DOCUMENT_CATEGORY);
            if (contextPropertiesById != null) {
                Map<String, Serializable> contextProperties = contextPropertiesById.get(id);
                if (contextProperties != null) {
                    context.getProperties().putAll(contextProperties);
                }
            }
            Event event = new EventImpl(eventName, context, Event.FLAG_NONE, time);
            eventService.fireEvent(event);
        }
    }
}
