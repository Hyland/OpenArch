/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.ecm.core.schema.types.resolver;

import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XRegistry;
import com.hyland.openarch.common.xmap.registry.XRegistryId;

/**
 * Handler for the {@link ObjectResolverService} "resolvers" extension point.
 *
 * @since 7.1
 */
@XObject("resolver")
@XRegistry
public class ObjectResolverDescriptor {

    @XNode("@type")
    @XRegistryId
    private String type;

    @XNode("@class")
    private Class<? extends ObjectResolver> resolver;

    public String getType() {
        return type;
    }

    public Class<? extends ObjectResolver> getResolver() {
        return resolver;
    }

    @Override
    public String toString() {
        return type + ": " + resolver.getCanonicalName();
    }

}
