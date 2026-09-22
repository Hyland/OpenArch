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

package org.nuxeo.ecm.core.security;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XNodeList;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XRegistry;
import com.hyland.openarch.common.xmap.registry.XRegistryId;

/**
 * @author Bogdan Stefanescu
 * @author Olivier Grisel
 * @author Thierry Delprat
 */
@XObject("permission")
@XRegistry
public class PermissionDescriptor {

    @XNode("@name")
    @XRegistryId
    private String name;

    @XNodeList(value = "include", type = String[].class, componentType = String.class)
    private String[] includePermissions;

    @XNodeList(value = "remove", type = String[].class, componentType = String.class)
    private String[] removePermissions;

    public String getName() {
        return name;
    }

    /**
     * Returns included permissions, filtering out removed permissions.
     *
     * @since 11.5
     */
    public List<String> getSubPermissions() {
        List<String> removed = getRemovePermissions();
        return getIncludePermissions().stream()
                                      .filter(Predicate.not(removed::contains))
                                      .distinct()
                                      .toList();
    }

    public List<String> getIncludePermissions() {
        return Arrays.asList(includePermissions);
    }

    public List<String> getRemovePermissions() {
        return Arrays.asList(removePermissions);
    }

    @Override
    public String toString() {
        return String.format("PermissionDescriptor[%s]", name);
    }

}
