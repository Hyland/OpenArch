/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.acl;

import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYONE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hyland.openarch.ecm.core.security.SecurityServiceImpl;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;
import com.hyland.openarch.ecm.core.api.security.ACE;
import org.springframework.stereotype.Component;

@Component
public class PermissionsManager {

    /**
     * Expands a NuxeoPrincipal into the list of principal strings used for ACL evaluation,
     * including external user mappings and external group memberships from Nucleus System Integration
     * if present on the principal's properties.
     *
     * Similar to the method {@link SecurityServiceImpl#expandPrincipal} but using the info from IDP
     */
    public List<String> expandUserPrincipal(NuxeoPrincipal principal) {
        Set<String> groups = new HashSet<>(principal.getGroups());
        List<String> principals = new ArrayList<>();
        principals.add(ACE.USER_PREFIX + principal.getId());
        principals.addAll(groups.stream().map(g -> ACE.GROUP_PREFIX + g).toList());
        principals.addAll(principal.getExternalUserMappings().stream().map(eu -> ACE.USER_PREFIX + eu).toList());
        principals.addAll(principal.getExternalGroupMappings().stream().map(eg -> ACE.GROUP_PREFIX + eg).toList());
        principals.add(EVERYONE);
        return principals;
    }

    public List<ACE> fetchFullListACEForUser(List<String> expandedUserNames, CoreSession session,
            DocumentRef documentRef) {
        if (expandedUserNames == null || expandedUserNames.isEmpty()) {
            return Collections.emptyList();
        }
        DocumentModel doc = session.getDocument(documentRef);
        List<ACE> acl = doc.getEffectiveACL();
        return acl.stream().filter(ace -> expandedUserNames.contains(ace.getPrincipal())).collect(Collectors.toList());
    }
}
