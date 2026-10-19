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
package com.hyland.openarch.ecm.core.schema;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;
import com.hyland.openarch.ecm.core.schema.dto.ModelMapCodec;
import com.hyland.openarch.ecm.core.schema.types.CompositeType;
import com.hyland.openarch.ecm.core.schema.types.Field;
import com.hyland.openarch.ecm.core.schema.types.Schema;
import com.hyland.openarch.ecm.core.schema.types.Type;

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

    /**
     * Gets the paths to the blob keys of all the blobs of all the document types and mixins.
     * <p>
     * Each "path" is a list of path components. Example:
     * {@code [[sysfile_blob, key], [sysfiles_blobs, blob, key], ...]}
     * <p>
     * A complex list contributes a single path, in which it is traversed without an index: the path
     * {@code [sysfiles_blobs, blob, key]} designates the blob key of every element of the {@code sysfiles_blobs} list.
     * <p>
     * This is computed from this schema manager, which is replaced when the content model is updated while the
     * application is running, so that the blob fields it declares are taken into account. A missing path means that
     * the blob key is not recorded in the document's {@code sys_blobKeys}, which makes the blob garbage collector
     * believe that the blob is not referenced and delete it.
     */
    List<List<String>> getBlobKeysPaths();

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

    /**
     * Checks whether the given property contains security-related repository state.
     *
     * @param name the property name
     * @return {@code true} if the property contains security-related state
     */
    boolean isSecurityProperty(String name);

    /**
     * Checks whether the given boolean property stores {@code false} as {@code null}.
     *
     * @param name the property name
     * @return {@code true} if the property stores {@code false} as {@code null}
     */
    boolean isFalseStoredAsNull(String name);

    /** The whole content model as a DTO. */
    ContentModelDTO toContentModel();

    /**
     * The whole content model as the untyped map the configuration service persists. Derived from
     * {@link #toContentModel()}, so there is only one place a model can be rendered from.
     */
    default Map<String, Object> toMap() {
        return ModelMapCodec.write(toContentModel());
    }

}
