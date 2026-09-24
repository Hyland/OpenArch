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
package com.hyland.openarch.ecm.core.api.impl;

import static java.util.Objects.requireNonNullElseGet;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;

/**
 * RepositoryPrincipal stub implementation.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class UserPrincipal implements RepositoryPrincipal, Serializable {

    private static final long serialVersionUID = 2013321088068583749L;

    protected boolean administrator;

    protected String userid;

    protected List<String> groups;

    protected String firstName;

    protected String lastName;

    protected String email;

    protected String username;

    protected transient String password;

    protected Map<String, Serializable> userAttributes;

    protected Map<String, Object> properties;

    protected String originatingId;

    public UserPrincipal(String userid) {
        this(userid, null, false);
    }

    public UserPrincipal(String userid, List<String> groups, boolean administrator) {
        this.userid = userid;
        this.groups = requireNonNullElseGet(groups, Collections::emptyList);
        this.administrator = administrator;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getFirstName() {
        return firstName;
    }

    @Override
    public String getLastName() {
        return lastName;
    }

    @Override
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @Override
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Override
    public String getId() {
        return userid;
    }

    @Override
    public void setId(String id) {
        this.userid = id;
    }

    @Override
    public List<String> getGroups() { // NOSONAR (method is overridden)
        return groups;
    }

    @Override
    public List<String> getAllGroups() {
        return groups;
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
    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public Map<String, Serializable> getUserAttributes() {
        return userAttributes;
    }

    @Override
    public void setUserAttributes(Map<String, Serializable> userAttributes) {
        this.userAttributes = userAttributes;
    }

    @Override
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public boolean isMemberOf(String group) {
        return groups.contains(group);
    }

    @Override
    public boolean isAdministrator() {
        return administrator;
    }

    @Override
    public String getOriginatingId() {
        return originatingId;
    }

    @Override
    public void setOriginatingPrincipal(String id) {
        this.originatingId = id;
    }

    @Override
    public String getActingId() {
        return getOriginatingId() == null ? getId() : getOriginatingId();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(userid)
                                    .append(groups)
                                    .append(firstName)
                                    .append(lastName)
                                    .append(email)
                                    .append(username)
                                    .append(password)
                                    .toHashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserPrincipal that)) {
            return false;
        }

        return new EqualsBuilder().append(userid, that.userid)
                                  .append(groups, that.groups)
                                  .append(firstName, that.firstName)
                                  .append(lastName, that.lastName)
                                  .append(email, that.email)
                                  .append(username, that.username)
                                  .append(password, that.password)
                                  .isEquals();
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
