/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.nuxeo.ecm.core.api.NuxeoGroup;

/**
 * @author <a href="mailto:glefter@nuxeo.com">George Lefter</a>
 */
public class NuxeoGroupImpl implements NuxeoGroup {

    private static final long serialVersionUID = 1L;

    private Map<String, Object> properties;

    protected GroupConfig config = GroupConfig.DEFAULT;

    public NuxeoGroupImpl(String id, String name) {
        if (name == null) {
            throw new IllegalArgumentException("group name cannot be null");
        }
        name = name.trim();

        properties = new HashMap<>();
        properties.put(config.idField, id);
        properties.put(config.nameField, name);
    }

    public NuxeoGroupImpl(Map<String, Object> entry, GroupConfig config) {
        this.properties = entry;
        this.config = config;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getMemberUsers() {
        return (List<String>) properties.get(config.membersField);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getMemberGroups() {
        return (List<String>) properties.get(config.subGroupsField);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getParentGroups() {
        return (List<String>) properties.get(config.parentGroupsField);
    }

    @Override
    public void setMemberUsers(List<String> users) {
        if (users == null) {
            throw new IllegalArgumentException("member users list cannot be null");
        }
        properties.put(config.membersField, users);
    }

    @Override
    public void setMemberGroups(List<String> groups) {
        if (groups == null) {
            throw new IllegalArgumentException("member groups list cannot be null");
        }
        properties.put(config.subGroupsField, groups);
    }

    @Override
    public void setParentGroups(List<String> groups) {
        if (groups == null) {
            throw new IllegalArgumentException("parent groups list cannot be null");
        }
        properties.put(config.parentGroupsField, groups);
    }

    @Override
    public String getName() {
        return (String) properties.get(config.nameField);
    }

    @Override
    public void setName(String name) {
        properties.put(config.nameField, name);
    }

    @Override
    public String getId() {
        return (String) properties.get(config.idField);
    }

    @Override
    public void setId(String id) {
        properties.put(config.idField, id);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof NuxeoGroupImpl ngi) {
            String name = getName();
            String otherName = ngi.getName();
            return Objects.equals(name, otherName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        String name = getName();
        return name == null ? 0 : name.hashCode();
    }

    @Override
    public String toString() {
        return getName();
    }

}
