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

import com.hyland.openarch.ecm.core.schema.types.CompositeType;

import java.util.Collection;
import java.util.Set;

/**
 * Document types are composite types made of several schemas.
 * <p>
 * Sample document types are Workspace, Section, Domain,... The list of builtin document type is visible at
 * NXCore/OSGI-INF/CoreExtensions.xml.
 */
public interface DocumentType extends CompositeType {

    /**
     * Tests whether this type describes a file or not.
     *
     * @return true if the type describes a file, otherwise returns false
     */
    boolean isFile();

    /**
     * Tests whether this type describes a folder or not.
     *
     * @return true if the type describes a folder, otherwise returns false
     */
    boolean isFolder();

    /**
     * Tests whether this type describe an ordered folder or not.
     *
     * @return true if the type describes an ordered folder, otherwise returns false
     */
    boolean isOrdered();

    /**
     * Gets all the mixins of this document type.
     * <p>
     * Mixins inherited from parents are taken into account.
     *
     * @return the mixins
     */
    Set<String> getMixins();

    /**
     * Returns {@code true} if this document type has the given mixin, {@code false otherwise}.
     *
     * @since 5.7
     */
    boolean hasMixin(String mixin);

    /**
     * Returns the types of the children that can be created inside this type.
     *
     * @since 8.4
     */
    Set<String> getSubtypes();

    /**
     * Sets the types of the children that can be created inside the this type.
     *
     * @since 8.4
     */
    void setSubtypes(Collection<String> subtypes);

    /**
     * Returns {@code true} if the given {@code subtype} type was explicitly allowed to be created inside this type.
     *
     * @since 8.4
     */
    boolean hasSubtype(String subtype);

    /**
     * Checks whether this type supports the full-text indexing.
     */
    boolean isFullTextIndexable();

    boolean isGovernable();
}
