/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.query.spec;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.schema.DocumentTypeImpl;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.FieldImpl;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;

/**
 * Implementation of {@link DocumentModel} relying on a map of properties.
 * <p>
 * This is useful to map request parameters to typed fields, used for query predicates resolution.
 */
public class MapDocumentModelProxy implements InvocationHandler {

    public static DocumentModel newDocumentModel(Map<String, Object> properties) {
        return (DocumentModel) Proxy.newProxyInstance(MapDocumentModelProxy.class.getClassLoader(),
                new Class<?>[] { DocumentModel.class }, new MapDocumentModelProxy(properties));
    }

    private final Map<String, Object> properties = new HashMap<>();

    public MapDocumentModelProxy(Map<String, Object> properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (name.equals("getPropertyValue")) {
            return properties.get(args[0]);
        } else if (name.equals("setPropertyValue")) {
            properties.put((String) args[0], args[1]);
        } else if (name.equals("getDocumentType")) {
            // FIXME: at least handle list use case, or plug-in ad-hoc conversion
            return new DocumentTypeImpl(null) {
                @Override
                public Field getField(String name) {
                    return new FieldImpl(name, StringType.INSTANCE, StringType.INSTANCE, null, null);
                }
            };
        }
        return null;
    }

}
