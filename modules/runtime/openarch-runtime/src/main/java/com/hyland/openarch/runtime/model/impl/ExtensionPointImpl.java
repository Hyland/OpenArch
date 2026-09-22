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

package com.hyland.openarch.runtime.model.impl;

import com.hyland.openarch.common.xmap.XMap;
import com.hyland.openarch.common.xmap.annotation.XContent;
import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XNodeList;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.annotation.XParent;
import com.hyland.openarch.runtime.model.ExtensionPoint;
import com.hyland.openarch.runtime.model.RegistrationInfo;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@XObject
public class ExtensionPointImpl implements ExtensionPoint {

    @XNode("@name")
    public String name;

    @XNode("@target")
    public String superComponent;

    @XContent("documentation")
    public String documentation;

    @XNodeList(value = "object@class", type = Class[].class, componentType = Class.class)
    public Class<?>[] contributions;

    public XMap xmap;

    @XParent
    public RegistrationInfo ri;

    /**
     * Potential registry class declaration for this extension point.
     *
     * @since 11.5
     */
    @XNode(value = "registry@class")
    protected String registryKlass;

    @Override
    public Class<?>[] getContributions() {
        return contributions;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDocumentation() {
        return documentation;
    }

    @Override
    public String getSuperComponent() {
        return superComponent;
    }

    @Override
    public String getRegistryClass() {
        return registryKlass;
    }

    @Override
    public XMap getXMap() {
        XMap localXMap = new XMap();
        for (Class<?> contrib : contributions) {
            if (contrib != null) {
                localXMap.register(contrib);
            } else {
                throw new RuntimeException(
                        "Unknown implementation class when contributing to " + ri.getComponent().getName());
            }
        }
        return localXMap;
    }

}
