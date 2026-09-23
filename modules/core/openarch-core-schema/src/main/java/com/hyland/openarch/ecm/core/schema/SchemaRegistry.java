/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.schema;

import java.util.Map;
import java.util.Set;

import com.hyland.openarch.common.xmap.Context;
import com.hyland.openarch.common.xmap.XAnnotatedObject;
import com.hyland.openarch.common.xmap.registry.Registry;
import org.w3c.dom.Element;

/**
 * Registry for multiple descriptors to the "schema" extension point.
 *
 * @since 11.5
 */
public class SchemaRegistry implements Registry {

    protected SchemaBindingRegistry schemaBindingRegistry = new SchemaBindingRegistry();

    @Override
    public void initialize() {
        schemaBindingRegistry.initialize();
    }

    @Override
    public void tag(String id) {
        schemaBindingRegistry.tag(id);
    }

    @Override
    public boolean isTagged(String id) {
        return schemaBindingRegistry.isTagged(id);
    }

    @Override
    public void register(Context ctx, XAnnotatedObject xObject, Element element, String tag) {
        Class<?> klass = xObject.getKlass();
        if (SchemaBindingDescriptor.class.equals(klass)) {
            schemaBindingRegistry.register(ctx, xObject, element, tag);
        } else {
            throw new IllegalArgumentException("Unsupported class " + klass);
        }

    }

    @Override
    public void unregister(String tag) {
        schemaBindingRegistry.unregister(tag);
    }

    // custom API

    public Map<String, SchemaBindingDescriptor> getSchemas() {
        return schemaBindingRegistry.getContributions();
    }

    public Set<String> getDisabledSchemas() {
        return schemaBindingRegistry.getDisabledContributions();
    }

}
