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
package org.nuxeo.ecm.platform.usermanager;

import static org.nuxeo.ecm.platform.usermanager.UserConfig.EXTERNAL_GROUP_MAPPINGS_COLUMN;
import static org.nuxeo.ecm.platform.usermanager.UserConfig.EXTERNAL_USER_MAPPINGS_COLUMN;
import static org.nuxeo.ecm.platform.usermanager.UserConfig.USER_ATTRIBUTES;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.runtime.api.Framework;

public class NuxeoPrincipalImpl implements NuxeoPrincipal {

    private static final long serialVersionUID = 1L;

    protected UserConfig config = UserConfig.DEFAULT;

    private boolean isAdministrator;

    private Map<String, Object> properties;
    private Map<String, Serializable> userAttributes;

    private String origUserId;

    /**
     * Constructor that sets principal to not anonymous, not administrator, and updates all the principal groups.
     */
    public NuxeoPrincipalImpl(String principalId) {
        this(principalId, false);
    }

    /**
     * Constructor that updates all the principal groups.
     */
    public NuxeoPrincipalImpl(String principalId, boolean isAdministrator) {
        properties = new HashMap<>();
        userAttributes = new HashMap<>();
        properties.put(config.idKey, principalId);
        this.isAdministrator = isAdministrator;
    }

    protected NuxeoPrincipalImpl(NuxeoPrincipalImpl other) {
        config = other.config;
        properties = new HashMap<>(other.properties);
        userAttributes = new HashMap<>(other.userAttributes);
        isAdministrator = other.isAdministrator;
        origUserId = other.origUserId;
    }

    public void setConfig(UserConfig config) {
        this.config = config;
    }

    public UserConfig getConfig() {
        return config;
    }

    @Override
    public String getFirstName() {
        return (String) properties.get(config.firstNameKey);
    }

    @Override
    public void setFirstName(String firstName) {
        properties.put(config.firstNameKey, firstName);
    }

    @Override
    public String getLastName() {
        return (String) properties.get(config.lastNameKey);
    }

    @Override
    public void setLastName(String lastName) {
        properties.put(config.lastNameKey, lastName);
    }

    @Override
    public String getId() {
        return (String) properties.get(config.idKey);
    }

    @Override
    public void setId(String id) {
        properties.put(config.idKey, id);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getGroups() {
        List<String> storedGroups = (List<String>) properties.get(config.groupsKey);
        return storedGroups != null ? new ArrayList<>(storedGroups) : new ArrayList<>();
    }

    @Override
    public List<String> getAllGroups() {
        return getGroups();
    }

    @Override
    public void setGroups(List<String> groups) {
        if (groups == null) {
            properties.put(config.groupsKey, List.of());
        } else {
            properties.put(config.groupsKey, groups);
        }
    }

    @Override
    public String getPassword() {
        // password should never be read at the UI level for safety reasons
        // + backend directories usually only store hashes that are useless
        // except to check authentication at the directory level
        return null;
    }

    @Override
    public void setPassword(String password) {
        properties.put(config.passwordKey, password);
    }

    @Override
    public String getEmail() {
        return (String) properties.get(config.emailKey);
    }

    @Override
    public void setEmail(String email) {
        properties.put(config.emailKey, email);
    }

    @Override
    public String getUsername() {
        return (String) properties.get(config.usernameKey);
    }

    @Override
    public void setUsername(String username) {
        properties.put(config.usernameKey, username);
    }

    @Override
    public Map<String, Serializable> getUserAttributes() {
        return new HashMap<>(userAttributes);
    }

    @Override
    public void setUserAttributes(Map<String, Serializable> userAttributes) {
            this.userAttributes.putAll(userAttributes);
    }

    /**
     * Returns a copy of all properties. User attributes are included as key-value pairs in the map.
     * @return the properties map without the password
     */
    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> res = new HashMap<>();
        res.putAll(getUserAttributes());
        res.putAll(properties);
        res.remove(config.passwordKey);
        return res;
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        Map<String, Object> props = new HashMap<>(properties);
        props.remove(config.idKey);
        // check if user attributes are present as map
        Object userAttrsValue = props.get(USER_ATTRIBUTES);
        if (userAttrsValue instanceof Map) {
            this.userAttributes.putAll((Map<? extends String, ? extends Serializable>) userAttrsValue);
            props.remove(USER_ATTRIBUTES);
        }
        this.properties.putAll(props);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getExternalUserMappings() {
        return (Set<String>) properties.getOrDefault(EXTERNAL_USER_MAPPINGS_COLUMN, Collections.emptySet());
    }

    @Override
    public void setExternalUserMappings(Set<String> externalUserMappings) {
        properties.put(EXTERNAL_USER_MAPPINGS_COLUMN, externalUserMappings);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getExternalGroupMappings() {
        return (Set<String>) properties.getOrDefault(EXTERNAL_GROUP_MAPPINGS_COLUMN, Collections.emptySet());
    }

    @Override
    public void setExternalGroupMappings(Set<String> externalGroupMappings) {
        properties.put(EXTERNAL_GROUP_MAPPINGS_COLUMN, externalGroupMappings);
    }

    @Override
    public boolean isMemberOf(String group) {
        return getGroups().contains(group);
    }

    @Override
    public boolean isAdministrator() {
        return isAdministrator;
    }

    @Override
    public String getOriginatingId() {
        return origUserId;
    }

    @Override
    public void setOriginatingPrincipal(String id) {
        origUserId = id;
    }

    @Override
    public String getActingId() {
        return getOriginatingId() == null ? getId() : getOriginatingId();
    }

    @Override
    public String toString() {
        return (String) properties.get(config.idKey);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Principal principal) {
            String id = getId();
            String otherName = principal.getName();
            if (id == null) {
                return otherName == null;
            } else {
                return id.equals(otherName);
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        String name = getId();
        return name == null ? 0 : name.hashCode();
    }

    public NuxeoPrincipal cloneTransferable() {
        return new TransferableClone(this);
    }

    /**
     * Provides another implementation which marshall the user id instead of transferring the whole content and resolve
     * it when unmarshalled.
     */
    protected static class TransferableClone extends NuxeoPrincipalImpl {

        protected TransferableClone(NuxeoPrincipalImpl other) {
            super(other);
        }

        static class DataTransferObject implements Serializable {

            private static final long serialVersionUID = 1L;

            final String userid;

            final String originatingId;

            DataTransferObject(NuxeoPrincipal principal) {
                userid = principal.getId();
                originatingId = principal.getOriginatingId();
            }

            private Object readResolve() throws ObjectStreamException {
                UserManager userManager = Framework.getService(UserManager.class);
                // look up principal as system user to avoid permission checks in directories
                NuxeoPrincipal principal = Framework.doPrivileged(() -> userManager.getPrincipal(userid));
                if (principal == null) {
                    throw new NullPointerException("No principal: " + userid);
                }
                principal.setOriginatingPrincipal(originatingId);
                return principal;
            }

        }

        private Object writeReplace() throws ObjectStreamException {
            return new DataTransferObject(this);
        }
    }
}
