/*
 * (C) Copyright 2022 Hyland (https://hyland.com/) and others.
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
 *     Adam Dunajewski
 */
package org.nuxeo.rest.document.utils;

import java.util.List;
import java.util.Objects;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.rest.acl.ACEDTO;
import org.nuxeo.rest.acl.ACEMapper;
import org.nuxeo.rest.document.DocumentDTO;
import org.springframework.stereotype.Component;

@Component
public class ACLChangesHandler {

    private final ACEMapper aceMapper;

    public ACLChangesHandler(ACEMapper aceMapper) {
        this.aceMapper = aceMapper;
    }

    public boolean isChangeInACL(DocumentDTO currentDocument, DocumentDTO incomingDocument) {
        List<ACEDTO> incomingACL = incomingDocument.getAcl();
        if (incomingACL == null) {
            // no updated ACL was provided, this is not considered a change
            return false;
        }
        List<ACEDTO> currentACL = convertNullListToEmptyList(currentDocument.getAcl());
        return !Objects.equals(incomingACL, currentACL);
    }

    private List<ACEDTO> convertNullListToEmptyList(List<ACEDTO> currentAces) {
        if (currentAces == null) {
            currentAces = List.of();
        }
        return currentAces;
    }

    public void applyACLChange(DocumentDTO updatingDocument, DocumentRef docRef, CoreSession session) {
        List<ACEDTO> aceDtos = updatingDocument.getAcl();
        ACL acl = new ACLImpl();
        aceDtos.forEach(acedto ->
                acl.add(aceMapper.aceDTOToAce(acedto)));
        session.setACL(docRef, acl, true);
    }

}
