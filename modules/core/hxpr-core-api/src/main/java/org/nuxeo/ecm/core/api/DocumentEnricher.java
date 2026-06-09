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

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Pluggable hook invoked when a document is created or updated, immediately alongside the
 * {@code sys_modified} stamping and within the same transaction.
 * <p>
 * The {@code reader}/{@code writer} target the in-flight, not-yet-persisted document, so anything
 * written is persisted in the same transaction. Downstream distributions (e.g. the enterprise
 * distribution) contribute implementations to the {@code enrichers} extension point of the
 * {@code org.nuxeo.ecm.core.DocumentEnrichmentService} component. Community runs zero enrichers by
 * default.
 */
public interface DocumentEnricher {

    /**
     * Called when a document is being created, after the system fields have been stamped.
     *
     * @param reader reads a property (system or schema) off the in-flight document
     * @param writer sets a property on the in-flight document
     */
    default void onCreate(Function<String, Object> reader, BiConsumer<String, Object> writer) {
        // no-op by default
    }

    /**
     * Called when an existing document is being updated, after the system fields have been stamped.
     *
     * @param reader reads a property (system or schema) off the in-flight document
     * @param writer sets a property on the in-flight document
     */
    default void onUpdate(Function<String, Object> reader, BiConsumer<String, Object> writer) {
        // no-op by default
    }
}
