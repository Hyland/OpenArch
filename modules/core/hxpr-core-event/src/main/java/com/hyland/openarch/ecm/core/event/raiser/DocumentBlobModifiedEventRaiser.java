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

import static com.hyland.openarch.ecm.core.api.event.DocumentEventTypes.DOCUMENT_BLOB_MODIFIED;
import com.hyland.openarch.ecm.core.event.EventService;
import com.hyland.openarch.ecm.core.event.impl.DocumentEventContext;
import com.hyland.openarch.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DocumentEventRaiser} bound to the {@code DOCUMENT_BLOB_MODIFIED} event. Builds a
 * {@link DocumentEventContext} from the {@link DocumentEventRaiserContext} and fires the event
 * through the {@link EventService}.
 *
 */
public class DocumentBlobModifiedEventRaiser implements DocumentEventRaiser {

    private static final Logger log = LoggerFactory.getLogger(DocumentBlobModifiedEventRaiser.class);

    @Override
    public void raise(DocumentEventRaiserContext context) {
        if (context.getSession() == null) {
            return;
        }
        log.debug("Raising '{}' events for {} document(s)", DOCUMENT_BLOB_MODIFIED,
                context.getDirtyBlobDocIds().size());
        DocumentEventSenderService documentEventSender = Framework.getService(DocumentEventSenderService.class);
        documentEventSender.sendDocumentEvents(context.getSession().getRepositoryName(), DOCUMENT_BLOB_MODIFIED,
                context.getDirtyBlobDocIds(), context.getPrincipal(), context.getContextForDocsWithDirtyBlobs());
    }
}
