/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.event.impl;

import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DeletedChildDocumentRecord;
import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;

import java.util.List;

public class GovernanceEventContext extends EventContextImpl {
    public GovernanceEventContext(CoreSession session, NuxeoPrincipal principal) {
        super(session, principal);
    }

    private List<DeletedChildDocumentRecord> deletedChildDocumentsData;

    public List<DeletedChildDocumentRecord> getDeletedChildDocumentsData() {
        return deletedChildDocumentsData;
    }

    public void setDeletedChildDocumentsData(List<DeletedChildDocumentRecord> deletedChildDocumentsData) {
        this.deletedChildDocumentsData = deletedChildDocumentsData;
    }
}
