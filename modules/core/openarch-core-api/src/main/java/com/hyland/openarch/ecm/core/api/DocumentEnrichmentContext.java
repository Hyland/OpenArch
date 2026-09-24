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
package com.hyland.openarch.ecm.core.api;

import java.time.Instant;

import com.hyland.openarch.ecm.core.model.InternalDocument;

/**
 * State passed to a {@link DocumentEnricher} on create or update: the acting principal, the operation timestamp, and
 * the in-flight (not-yet-persisted) document itself.
 * <p>
 * Enrichers can read and update the document through {@link #getDocument()} and the changes will be persisted in the
 * same transaction. The timestamp is shared across all documents of a single save, so enrichers should prefer it over
 * {@code Instant.now()}.
 */
public class DocumentEnrichmentContext {

    private final NuxeoPrincipal principal;

    private final Instant timestamp;

    private final InternalDocument document;

    public DocumentEnrichmentContext(NuxeoPrincipal principal, Instant timestamp, InternalDocument document) {
        this.principal = principal;
        this.timestamp = timestamp;
        this.document = document;
    }

    /** @return the principal performing the create/update, may be {@code null} */
    public NuxeoPrincipal getPrincipal() {
        return principal;
    }

    /** @return the operation timestamp, shared across all documents of a single save */
    public Instant getTimestamp() {
        return timestamp;
    }

    /** @return the in-flight document being created or updated; mutations are persisted in the current transaction */
    public InternalDocument getDocument() {
        return document;
    }
}
