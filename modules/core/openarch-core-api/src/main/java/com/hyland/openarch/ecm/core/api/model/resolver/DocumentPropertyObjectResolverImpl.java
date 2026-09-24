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

package com.hyland.openarch.ecm.core.api.model.resolver;

import java.util.List;

import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver;

/**
 * @since 7.1
 */
public class DocumentPropertyObjectResolverImpl implements PropertyObjectResolver {

    protected DocumentModel doc;

    protected String xpath;

    protected ObjectResolver resolver;

    public static DocumentPropertyObjectResolverImpl create(DocumentModel doc, String xpath) {
        SchemaManager schemaManager = doc.getCoreSession().getSchemaManager();
        Field field = schemaManager.getField(xpath);
        if (field != null) {
            ObjectResolver resolver = field.getType().getObjectResolver();
            if (resolver != null) {
                return new DocumentPropertyObjectResolverImpl(doc, xpath, resolver);
            }
        }
        return null;
    }

    public DocumentPropertyObjectResolverImpl(DocumentModel doc, String xpath, ObjectResolver resolver) {
        this.doc = doc;
        this.xpath = xpath;
        this.resolver = resolver;
    }

    @Override
    public List<Class<?>> getManagedClasses() {
        return resolver.getManagedClasses();
    }

    @Override
    public boolean validate() {
        return resolver.validate(doc.getPropertyValue(xpath), doc.getCoreSession());
    }

    @Override
    public boolean validate(Object context) {
        return resolver.validate(doc.getPropertyValue(xpath), context);
    }

    @Override
    public Object fetch() {
        return resolver.fetch(doc.getPropertyValue(xpath), doc.getCoreSession());
    }

    @Override
    public Object fetch(Object context) {
        return resolver.fetch(doc.getPropertyValue(xpath), context);
    }

    @Override
    public <T> T fetch(Class<T> type) {
        return resolver.fetch(type, doc.getPropertyValue(xpath));
    }

    @Override
    public void setObject(Object object) {
        Object reference = resolver.getReference(object);
        doc.setPropertyValue(xpath, reference);
    }

    @Override
    public ObjectResolver getObjectResolver() {
        return resolver;
    }

}
