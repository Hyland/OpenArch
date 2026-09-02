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


/**
 * Pluggable hook that knows how to raise a single kind of document event (for example
 * {@code DOCUMENT_UPDATED} or {@code DOCUMENT_BLOB_MODIFIED}).
 * <p>
 * Implementations are contributed to the {@code raisers} extension point of the
 * {@code org.nuxeo.ecm.core.event.raise.DocumentEventRaiserService} component, each one bound to an
 * event name. The {@link DocumentEventRaiserService} looks up the raiser bound to the requested
 * event name and delegates to {@link #raise}.
 *
 */
public interface DocumentEventRaiser {

    /**
     * Raises the document event described by the given context.
     *
     * @param context the context carrying the event name, document, principal and additional event
     *            context properties
     */
    void raise(DocumentEventRaiserContext context);
}
