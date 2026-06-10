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

/**
 * Fans out document create/update events to the registered {@link DocumentEnricher} contributions.
 * <p>
 * Looked up from the core create/update code path via {@code Framework.getService}. When no
 * enrichers are registered the calls are cheap no-ops.
 */
public interface DocumentEnrichmentService {

    /**
     * Invokes {@link DocumentEnricher#onCreate} on every registered enricher.
     */
    void enrichOnCreate(DocumentEnrichmentContext context);

    /**
     * Invokes {@link DocumentEnricher#onUpdate} on every registered enricher.
     */
    void enrichOnUpdate(DocumentEnrichmentContext context);
}
