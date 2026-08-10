/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.ecm.core.schema;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;

/**
 * Represents a document type that is unknown.
 * Provides default implementations for all methods, returning exceptions or some default values.
 *
 * @since 2024
 */
public class UnknownDocumentType implements DocumentType {
    private static final String ERROR_MESSAGE_PREFIX = "Unknown document type: ";
    private final String name;

    public UnknownDocumentType(String name) {
        this.name = name;
    }

    private String getErrorMessage() {
        return ERROR_MESSAGE_PREFIX + name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type getSuperType() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean isSimpleType() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean isComplexType() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean isListType() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean isCompositeType() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean validate(Object object) {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Object decode(String string) {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public String encode(Object object) {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Object convert(Object value) throws TypeException {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Set<Constraint> getConstraints() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public ObjectResolver getObjectResolver() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Map<String, Object> toTypeDTO(SchemaManager schemaManager) {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean isFile() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean isFolder() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean isOrdered() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Set<String> getMixins() {
        return Collections.emptySet();
    }

    @Override
    public boolean hasMixin(String mixin) {
        return false;
    }

    @Override
    public Set<String> getSubtypes() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public void setSubtypes(Collection<String> subtypes) {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean hasSubtype(String subtype) {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public String getPrefix() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public String getProjectId() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Field getField(String name) {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Field addField(String name, Type type, String defaultValue, Collection<Constraint> constraints) {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean hasField(String name) {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean hasFields() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Collection<Field> getFields() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public int getFieldsCount() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean isFullTextIndexable() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean isGovernable() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Schema getSchema(String name) {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public boolean hasSchema(String name) {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Set<String> getSchemaNames() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Collection<Schema> getSchemas() {
        throw new UnsupportedOperationException(getErrorMessage());
    }

    @Override
    public Map<String, Object> toMixinTypeDTO(boolean noPerDocumentQuery) {
        throw new UnsupportedOperationException(getErrorMessage());
    }
}
