/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Bogdan Stefanescu
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.api.security.impl;

import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYONE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYTHING;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.Access;
import org.nuxeo.ecm.core.api.security.AdministratorGroupsProvider;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.runtime.api.Framework;

/**
 * An ACL implementation.
 */
public class ACLImpl extends ArrayList<ACE> implements ACL {

    private static final long serialVersionUID = 1L;

    public ACLImpl() {
        // nothing
    }

    @Override
    public boolean blockInheritance(String username) {
        if (this.contains(ACE.BLOCK)) {
            return false;
        }
        List<ACE> aces = new ArrayList<>();
        aces.add(ACE.builder().user(username).permission(SecurityConstants.EVERYTHING).creator(username).build());
        aces.addAll(getAdminEverythingACES());
        aces.add(ACE.BLOCK);
        this.addAll(0, aces);
        return true;
    }

    @Override
    public boolean unblockInheritance() {
        return this.remove(ACE.BLOCK);
    }

    @Override
    public boolean isBlockingInheritance() {
        for (ACE ace : this) {
            if (ace.isEffective() && ace.isBlockingInheritance()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean add(ACE ace) {
        // don't insert duplicates
        if (this.contains(ace)) {
            return false;
        }
        int index = this.indexOf(ACE.BLOCK);
        if (index >= 0) {
            // add before block
            super.add(index, ace);
        } else {
            // add at end
            super.add(ace);
        }
        return true;
    }

    protected List<ACE> getAdminEverythingACES() {
        List<ACE> aces = new ArrayList<>();
        AdministratorGroupsProvider provider = Framework.getService(AdministratorGroupsProvider.class);
        List<String> administratorsGroups = provider.getAdministratorsGroups();
        for (String adminGroup : administratorsGroups) {
            aces.add(ACE.builder().group(adminGroup).permission(SecurityConstants.EVERYTHING).build());
        }
        return aces;
    }

    @Override
    public boolean replace(ACE oldACE, ACE newACE) {
        int index = this.indexOf(oldACE);
        if (index == -1) {
            return false;
        }
        super.remove(oldACE);
        super.add(index, newACE);
        return true;
    }

    @Override
    public boolean removeByPrincipal(String principal) {
        boolean aclChanged = false;
        for (Iterator<ACE> it = this.iterator(); it.hasNext();) {
            ACE ace = it.next();
            if (ace.getPrincipal().equals(principal)) {
                it.remove();
                aclChanged = true;
            }
        }
        return aclChanged;
    }

    @Override
    public ACL clone() {
        ACLImpl copy = new ACLImpl();
        for (ACE ace : this) {
            copy.add(ace.clone());
        }
        return copy;
    }

    @Override
    public Access getAccess(Collection<String> principals, Collection<String> permissions) {
        for (ACE ace : this) {
            if (ace.isEffective()) {
                String acePerm = ace.getPermission();
                String aceUser = ace.getPrincipal();
                for (String principal : principals) {
                    if (principalsMatch(aceUser, principal)) {
                        for (String permission : permissions) {
                            if (permissionsMatch(acePerm, permission)) {
                                return ace.isGranted() ? Access.GRANT : Access.DENY;
                            }
                        }
                    }
                }
            }
        }
        return Access.UNKNOWN;
    }

    private static boolean permissionsMatch(String acePerm, String permission) {
        return EVERYTHING.equals(acePerm) || acePerm.equals(permission);
    }

    private static boolean principalsMatch(String acePrincipal, String principal) {
        return EVERYONE.equals(acePrincipal) || acePrincipal.equals(principal);
    }

}
