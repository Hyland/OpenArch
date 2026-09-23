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
package com.hyland.openarch.ecm.core.schema.types;

/**
 * A list of typed objects.
 * <p>
 * The List type can validate java <code>array</code> and/or java <code>Collection</code>.
 */
public interface ListType extends Type {

    /**
     * Get the field describing the element type the list accept.
     *
     * @return the field describing the list element types
     */
    Type getFieldType();

    /**
     * Get the field defining the elements stored by this list.
     *
     * @return the field
     */
    Field getField();

    /**
     * Whether the instances of this list are arrays.
     */
    boolean isArray();

}
