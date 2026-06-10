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
package org.nuxeo.ecm.core.api;

import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * State passed to a {@link DocumentEnricher} on create or update: the acting principal, the
 * operation timestamp, and property accessors for the in-flight (not-yet-persisted) document.
 * Property writes are persisted in the same transaction, and the timestamp is shared across all
 * documents of a single save, so enrichers should prefer it over {@code Instant.now()}.
 */
public class DocumentEnrichmentContext {

    private final NuxeoPrincipal principal;

    private final Instant timestamp;

    private final Function<String, Object> reader;

    private final BiConsumer<String, Object> writer;

    public DocumentEnrichmentContext(NuxeoPrincipal principal, Instant timestamp, Function<String, Object> reader,
            BiConsumer<String, Object> writer) {
        this.principal = principal;
        this.timestamp = timestamp;
        this.reader = reader;
        this.writer = writer;
    }

    /** @return the principal performing the create/update, may be {@code null} */
    public NuxeoPrincipal getPrincipal() {
        return principal;
    }

    /** @return the operation timestamp, shared across all documents of a single save */
    public Instant getTimestamp() {
        return timestamp;
    }

    /** Reads a property (system or schema, e.g. {@code sys_primaryType}) off the in-flight document. */
    public Object getProperty(String name) {
        return reader.apply(name);
    }

    /** Sets a property on the in-flight document; persisted in the current transaction. */
    public void setProperty(String name, Object value) {
        writer.accept(name, value);
    }
}
