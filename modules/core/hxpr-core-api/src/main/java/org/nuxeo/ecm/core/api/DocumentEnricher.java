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
package org.nuxeo.ecm.core.api;

/**
 * Pluggable hook invoked when a document is created or updated, immediately alongside the
 * {@code sys_modified} stamping and within the same transaction.
 * <p>
 * Community itself ships the {@code SystemPropertiesEnricher} against this hook to stamp the
 * {@code sys_*} system fields; downstream distributions (e.g. the enterprise distribution) contribute
 * further implementations to the {@code enrichers} extension point of the
 * {@code org.nuxeo.ecm.core.DocumentEnrichmentService} component.
 */
public interface DocumentEnricher {

    /**
     * Called when a document is being created, before it is persisted.
     *
     * @param context the in-flight document being created
     */
    default void onCreate(DocumentEnrichmentContext context) {
        // no-op by default
    }

    /**
     * Called when an existing document is being updated, before it is persisted.
     *
     * @param context the in-flight document being updated
     */
    default void onUpdate(DocumentEnrichmentContext context) {
        // no-op by default
    }
}
