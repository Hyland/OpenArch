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

import org.nuxeo.ecm.core.api.NuxeoPrincipal;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * Service responsible for firing core document events (for example {@code DOCUMENT_CREATED} or
 * {@code DOCUMENT_UPDATED}) for a set of documents.
 * <p>
 * The event sending logic was extracted from {@code DBSTransactionState} so that it can be reused
 * and unit-tested independently of the transaction state.
 */
public interface DocumentEventSenderService {

    /**
     * Sends a core event for each document in the given list.
     *
     * @param repositoryName the repository name used to open the system core session
     * @param eventName the name of the event to fire
     * @param ids the ids of the documents for which an event must be fired
     * @param principal the principal to associate with the event
     * @param contextPropertiesById optional per-document context properties, keyed by document id
     */
    void sendDocumentEvents(String repositoryName, String eventName, Set<String> ids, NuxeoPrincipal principal,
            Map<String, Map<String, Serializable>> contextPropertiesById);
}
