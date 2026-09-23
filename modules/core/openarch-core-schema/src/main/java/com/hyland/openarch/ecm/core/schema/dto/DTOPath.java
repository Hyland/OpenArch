/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.schema.dto;

import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.ANONYMOUS_TYPE;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.PATH_DELIMITER;

/**
 * Where a validation violation is, and what the offending value was.
 * <p>
 * Replaces the tree cursor that used to walk the untyped model map: violations only ever needed a
 * readable path such as {@code primaryTypes/SysFolder/extends} and the value found there, never the
 * ability to navigate.
 *
 * @param path the readable path, e.g. {@code schemas/system/fields/sys_title}
 * @param key the last path segment
 * @param value the offending value, or {@code null} if there was nothing at that path
 */
public record DTOPath(String path, String key, Object value) {

    /** A top-level section of the model, such as {@code types} or {@code primaryTypes}. */
    public static DTOPath root(String key, Object value) {
        return new DTOPath(key, key, value);
    }

    public DTOPath child(String childKey, Object childValue) {
        return new DTOPath(path + PATH_DELIMITER + childKey, childKey, childValue);
    }

    /** Same location, different value; used when the value is only known later. */
    public DTOPath withValue(Object newValue) {
        return new DTOPath(path, key, newValue);
    }

    /** An inline type declared by a field, which has no name of its own. */
    public DTOPath anonymous() {
        return new DTOPath(ANONYMOUS_TYPE, ANONYMOUS_TYPE, value);
    }

    /** The value to report, falling back to the key when there is nothing at the path. */
    public Object invalidValue() {
        return value == null ? key : value;
    }
}
