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
package org.nuxeo.ecm.core.model;

import java.util.List;

import org.nuxeo.ecm.core.api.Lock;

/**
 * Outcome of a document write, including any events that the caller must publish.
 *
 * @param events events to publish
 * @param lock the lock returned by a lock operation, or {@code null} for other writes
 */
public record WriteResult(List<DocumentEventSpec> events, Lock lock) {

    public static final WriteResult EMPTY = new WriteResult(null, null);

    public WriteResult {
        events = events == null ? List.of() : events;
    }

    public static WriteResult of(DocumentEventSpec... events) {
        return events.length == 0 ? EMPTY : new WriteResult(List.of(events), null);
    }

    public static WriteResult of(Lock lock, DocumentEventSpec... events) {
        return new WriteResult(List.of(events), lock);
    }
}
