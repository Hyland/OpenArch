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

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * State passed to a {@link DocumentEventRaiser} when raising a document event: the name of the event
 * to raise, the document it relates to, the acting principal and the additional context properties
 * to stamp on the fired event.
 * the {@code DocumentEventContext} of the raised event.
 *
 */
public class DocumentEventRaiserContext {

    private final CoreSession coreSession;

    private final NuxeoPrincipal principal;

    private final Map<String, Map<String, Serializable>> docWithDirtyProps;

    private final Map<String, Map<String, Serializable>> docWithDirtyBlobs;

    private final Set<String> dirtyPropDocIds;

    private final long time;

    public DocumentEventRaiserContext(CoreSession session, NuxeoPrincipal principal,
              Map<String, Map<String, Serializable>> contextForDocsWithDirtyProps, Map<String, Map<String, Serializable>> contextForDocsWithDirtyBlobs,
              Set<String> dirtyPropDocIds, long time) {
        this.coreSession = session;
        this.principal = principal;
        this.docWithDirtyProps = contextForDocsWithDirtyProps == null ? new HashMap<>() : new HashMap<>(contextForDocsWithDirtyProps);
        this.docWithDirtyBlobs = contextForDocsWithDirtyBlobs == null ? new HashMap<>() : new HashMap<>(contextForDocsWithDirtyBlobs);
        this.dirtyPropDocIds = dirtyPropDocIds;
        this.time = time;
    }

    /** @return the session the event relates to */
    public CoreSession getSession() {
        return coreSession;
    }

    /** @return the principal on behalf of whom the event is raised, may be {@code null} */
    public NuxeoPrincipal getPrincipal() {
        return principal;
    }

    /** @return the event time as epoch milliseconds */
    public Set<String> getDirtyPropDocIds() {
        return dirtyPropDocIds;
    }

    public Set<String> getDirtyBlobDocIds() {
        return docWithDirtyBlobs.keySet();
    }

    /** @return the mutable event context doc with dirty properties, never {@code null} */
    public Map<String, Map<String, Serializable>> getContextForDocsWithDirtyProps() {
        return docWithDirtyProps;
    }

    /** @return the mutable event context doc with dirty blobs, never {@code null} */
    public Map<String, Map<String, Serializable>> getContextForDocsWithDirtyBlobs() {
        return docWithDirtyBlobs;
    }

    public long getTime() {
        return time;
    }
}
