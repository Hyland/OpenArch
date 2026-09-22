/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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

import java.util.List;

public class SchemaManagerConstants {

    private SchemaManagerConstants() {
        // Utility class, should not be instantiated
    }

    public static final String SYSTEM_SCHEMA = "system";

    public static final List<String> SYSTEM_SCHEMAS = List.of(SYSTEM_SCHEMA, "sysgovernable", "sysversionable");

    public static final String ANONYMOUS_TYPE = "#anonymous";

    public static final String COMPLEX = "complex";

    public static final String CONSTRAINTS = "constraints";

    public static final String EXTENDS = "extends";

    public static final String DEFAULT = "default";

    public static final String ENUMERATION = "enumeration";

    public static final String FIELDS = "fields";

    public static final String FULL_TEXT_INDEXED = "fullTextIndexed";

    public static final String ITEM = "item";

    public static final String LIST = "list";

    public static final String MAX_EXCLUSIVE = "maxExclusive";

    public static final String MAX_INCLUSIVE = "maxInclusive";

    public static final String MAX_LENGTH = "maxLength";

    public static final String MANAGED = "managed";

    public static final String MESSAGE_KEY_SEPARATOR = ".";

    public static final String MIN_EXCLUSIVE = "minExclusive";

    public static final String MIN_INCLUSIVE = "minInclusive";

    public static final String MIN_LENGTH = "minLength";

    public static final String MIXIN_TYPES = "mixinTypes";

    public static final String MIXINS = "mixins";

    public static final String MODIFIED = "modified";

    public static final String NO_PER_DOCUMENT_QUERY = "noPerDocumentQuery";

    public static final String NULLABLE = "nullable";

    public static final String PATTERN = "pattern";

    public static final String PREFIX = "prefix";

    public static final String PROJECT_ID = "projectId";

    public static final String RESOLVER = "resolver";

    public static final String PATH_DELIMITER = "/";

    public static final String PRIMARY_TYPES = "primaryTypes";

    public static final String SCHEMAS = "schemas";

    public static final String STRING = "string";

    public static final String SUBTYPES = "subtypes";

    public static final String SUPERTYPES = "supertypes";

    public static final String TYPE = "type";

    public static final String TYPES = "types";

    public static final String VERSION_WRITABLE = "versionWritable";

}
