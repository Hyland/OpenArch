/*
 * (C) Copyright 2025 Hyland (https://hyland.com/) and others.
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
 *
 * Contributors:
 *     Abdul Mohammed
 */

package org.nuxeo.ecm.core.event.impl;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;

/**
 * Minimal eventContext implementation that can be used for events that are not bound to a CoreSession.
 * Used by OpenSearch inline listener.
 * @author Abdul Mohammed
 */
public class OpenSearchDeleteDocumentEventContext extends EventContextImpl {

    private static final long serialVersionUID = 1L;

    private String docId;

    public OpenSearchDeleteDocumentEventContext(NuxeoPrincipal principal) {
        super(null, principal);
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }
}
