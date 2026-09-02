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

import java.util.List;

/**
 * Service responsible for raising different kinds of document events (for example
 * {@code DOCUMENT_UPDATED} or {@code DOCUMENT_BLOB_MODIFIED}) through pluggable
 * {@link DocumentEventRaiser} contributions.
 * <p>
 * The service acts as an extension point: each contributed raiser is bound to an event name and the
 * service dispatches a call to the raiser bound to the requested event.
 *
 */
public interface DocumentEventRaiserService {

    /**
     * Raises the document event described by the given context, using the {@link DocumentEventRaiser}
     * bound to the context's event name.
     *
     * @param context the context carrying the event name, document, principal and additional event
     *            context properties
     */
    void raiseEvent(DocumentEventRaiserContext context);
}
