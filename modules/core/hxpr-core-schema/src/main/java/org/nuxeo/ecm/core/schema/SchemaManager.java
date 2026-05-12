/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Bogdan Stefanescu
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.schema;

import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.Type;

/**
 * The Schema Manager manages core document types, schemas, mixins and field types.
 */
public interface SchemaManager {

    /** Gets a global type. */
    Type getType(String typeName);

    /** Gets a schema. */
    Schema getSchema(String schema);

    /** Gets the list of schemas. */
    Schema[] getSchemas();

    /** Gets a document type. */
    DocumentType getDocumentType(String docType);

    /** Gets the list of document types. */
    DocumentType[] getDocumentTypes();

    /** Gets a mixin. */
    CompositeType getMixin(String name);

    /** Gets the list of mixins. */
    CompositeType[] getMixins();

    /** Finds which mixins are configured as no-per-instance-query. */
    Set<String> getNoPerDocumentQueryMixins();

    /**
     * Returns the field with given xpath, or null if not found.
     */
    Field getField(String xpath);

    /**
     * Returns the names of all document types that have given mixin.
     *
     * @return null or the names as a guaranteed non-empty set.
     */
    Set<String> getDocumentTypeNamesForMixin(String mixin);

    /**
     * Return the names of all document types extending the given one, which is included.
     *
     * @return null or the set of names.
     */
    Set<String> getDocumentTypeNamesExtending(String docType);

    /**
     * Return the names of all document types extended by the given one, which is included.
     *
     * @return null or the set of names.
     */
    Set<String> getDocumentTypeNamesExtendedBy(String docType);

    Map<String, Object> toDTO();

}
