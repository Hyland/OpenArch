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
package org.nuxeo.ecm.core.schema.types;

import java.util.Set;

import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;

/**
 * A field is a member of a complex type.
 * <p>
 * It is defined by a name and a type.
 */
public interface Field {

    int NILLABLE = 1;

    /**
     * Gets the field name.
     *
     * @return the field name
     */
    String getName();

    /**
     * Gets the field type.
     *
     * @return the field type
     */
    Type getType();

    /**
     * Gets the complex type or list type that declared this field.
     * <p>
     * The declaring type may differ from the complex type owning this field.
     * <p>
     * For example, in the case of a derived complex type, the field is owned by both the derived type and the base
     * type, but it's declared only by the base type.
     *
     * @return the complex that declared this field
     */
    Type getDeclaringType();

    /**
     * Gets this field default value or null if none.
     *
     * @return the default value if any was specified, null otherwise
     */
    Object getDefaultValue();

    /**
     * Checks whether this field can have null values.
     *
     * @return true if the field can have null values
     */
    boolean isNullable();

    /**
     * @return The constraints applied to this field.
     * @since 7.1
     */
    Set<Constraint> getConstraints();

    Object toFieldDtoOrReference(SchemaManager schemaManager);

    /**
     * Returns true if the property 'fullTextIndexed' is defined on the field.
     */
    boolean isFullTextIndexed();
}
