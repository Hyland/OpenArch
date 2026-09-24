/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.ecm.core.api;

import static com.hyland.openarch.runtime.api.login.LoginService.SYSTEM_USERNAME;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hyland.openarch.ecm.core.api.security.SecurityConstants;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class SystemPrincipal implements RepositoryPrincipal {

    private static final long serialVersionUID = -3381784063138281706L;

    private static final char[] SYS_PASSWORD = null;

    private static final List<String> SYS_GROUPS = List.of(SecurityConstants.ADMINISTRATORS_GROUP);

    private String origUserId;

    private int hash;

    public SystemPrincipal() {
        this(null);
    }

    public SystemPrincipal(String origUserId) {
        this.origUserId = origUserId == null ? SYSTEM_USERNAME : origUserId;
        computeHash();
    }

    private void computeHash() {
        if (origUserId != null) {
            hash = (SYSTEM_USERNAME + "-" + origUserId).hashCode();
        } else {
            hash = SYSTEM_USERNAME.hashCode();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SystemPrincipal systemPrincipal) {
            if (origUserId == null) {
                return systemPrincipal.origUserId == null;
            } else {
                return origUserId.equals(systemPrincipal.origUserId);
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String getEmail() {
        return null;
    }

    @Override
    public void setEmail(String email) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getFirstName() {
        return "System";
    }

    @Override
    public String getLastName() {
        return "System";
    }

    @Override
    public String getId() {
        return SYSTEM_USERNAME;
    }

    @Override
    public void setId(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getGroups() { // NOSONAR (method is overridden)
        return SYS_GROUPS;
    }

    @Override
    public List<String> getAllGroups() {
        return SYS_GROUPS;
    }

    @Override
    public Set<String> getExternalUserMappings() {
        return Collections.emptySet();
    }

    @Override
    public void setExternalUserMappings(Set<String> externalUserMappings) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getExternalGroupMappings() {
        return Collections.emptySet();
    }

    @Override
    public void setExternalGroupMappings(Set<String> externalGroupMappings) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPassword() {
        if (SYS_PASSWORD == null) {
            return null;
        }
        return new String(SYS_PASSWORD);
    }

    @Override
    public String getOriginatingId() {
        return origUserId;
    }

    @Override
    public void setOriginatingPrincipal(String id) {
        origUserId = id;
        computeHash();
    }

    @Override
    public String getActingId() {
        return getOriginatingId() == null ? getId() : getOriginatingId();
    }

    @Override
    public Map<String, Serializable> getUserAttributes() {
        return Map.of();
    }

    @Override
    public Map<String, Object> getProperties() {
        return null;
    }

    @Override
    public void setFirstName(String firstName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLastName(String lastName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setGroups(List<String> groups) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPassword(String password) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setUserAttributes(Map<String, Serializable> userAttributes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMemberOf(String group) {
        return SYS_GROUPS.contains(group);
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public boolean isAdministrator() {
        return true;
    }

}
