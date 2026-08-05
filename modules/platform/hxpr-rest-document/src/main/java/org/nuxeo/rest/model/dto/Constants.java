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
package org.nuxeo.rest.model.dto;

public class Constants {

    private Constants(){
        // utility class
    }

    // Model API REST Path
    public static final String API_PATH = "/api/repository/model";


    // DTO constants
    public static final String CONSTRAINTS = "constraints";
    public static final String DEFAULT = "default";
    public static final String ENUMERATION = "enumeration";
    public static final String EXTENDS = "extends";
    public static final String FULL_TEXT_INDEXED = "fullTextIndexed";
    public static final String FIELDS = "fields";
    public static final String LIST = "list";
    public static final String MAX_EXCLUSIVE = "maxExclusive";
    public static final String MAX_INCLUSIVE = "maxInclusive";
    public static final String MAX_LENGTH = "maxLength";
    public static final String MIN_EXCLUSIVE = "minExclusive";
    public static final String MIN_INCLUSIVE = "minInclusive";
    public static final String MIN_LENGTH = "minLength";
    public static final String MIXINS = "mixins";
    public static final String MIXIN_TYPES = "mixinTypes";
    public static final String NAME = "name";
    public static final String NO_PER_DOCUMENT_QUERY = "noPerDocumentQuery";
    public static final String NULLABLE = "nullable";
    public static final String PATTERN = "pattern";
    public static final String PREFIX = "prefix";
    public static final String PRIMARY_TYPES = "primaryTypes";
    public static final String PROJECT_ID = "projectId";
    public static final String REPOSITORY = "repository";
    public static final String RESOLVER = "resolver";
    public static final String SCHEMAS = "schemas";
    public static final String STORE = "store";
    public static final String SUBTYPES = "subtypes";
    public static final String TYPE = "type";
    public static final String TYPES = "types";
    public static final String VALIDATION = "validation";
    public static final String VERSION_WRITABLE = "versionWritable";
    public static final String VOCABULARY = "vocabulary";

    // For error reporting only
    public static final String MIXINTYPE = "MixinType";
    public static final String PRIMARYTYPE = "PrimaryType";
    public static final String SCHEMA = "Schema";
}
