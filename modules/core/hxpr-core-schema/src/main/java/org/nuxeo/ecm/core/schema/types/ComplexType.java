/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.schema.types;

import java.util.Collection;

import org.nuxeo.ecm.core.schema.types.constraints.Constraint;

/**
 * A complex type is tree-like structure of named elements which can be of any type.
 * <p>
 * Complex types can describe and validate java <code>Map objects</code>.
 */
public interface ComplexType extends Type {

    /**
     * Gets the prefix used by this complex type.
     *
     * @return the prefix, never {@code null}
     */
    String getPrefix();

    /**
     * Gets the field with the given name.
     *
     * @param name the field name
     * @return the field
     */
    Field getField(String name);

    /**
     * Adds a field to this complex type.
     *
     * @param name the field name
     * @param type the field type
     * @param defaultValue an optional default value (null if none)
     * @return the created field
     */
    Field addField(String name, Type type, String defaultValue, Collection<Constraint> constraints);

    /**
     * Tests whether this type defines the given field name.
     *
     * @param name the field name
     * @return true if the field exists, false otherwise
     */
    boolean hasField(String name);

    /**
     * Tests whether this type has any field defined.
     * <p>
     * If a complex type has no fields, it is considered as unstructured and it accepts any field with any type and
     * name.
     *
     * @return true if the at least one field exists, false otherwise
     */
    boolean hasFields();

    /**
     * Gets all fields as a (field name, field type) map.
     *
     * @return the fields map
     */
    Collection<Field> getFields();

    /**
     * Gets the number of fields defined for this complex type.
     *
     * @return the fields count
     */
    int getFieldsCount();

}
