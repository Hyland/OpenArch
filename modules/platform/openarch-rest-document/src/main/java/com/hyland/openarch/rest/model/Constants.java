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
package com.hyland.openarch.rest.model;

import com.hyland.openarch.ecm.core.schema.SchemaManagerConstants;

/**
 * Constants for the Model REST API.
 * <p>
 * The names of the content model's JSON properties are not here: they belong to the DTOs in
 * {@code com.hyland.openarch.ecm.core.schema.dto} and to
 * {@link SchemaManagerConstants SchemaManagerConstants}, which is the single
 * place they are declared.
 */
public class Constants {

    private Constants() {
        // utility class
    }

    // Model API REST Path
    public static final String API_PATH = "/api/repository/model"; // NOSONAR

    // Model API REST Path (v2, with optimistic locking)
    public static final String API_PATH_V2 = "/api/v2/repository/model"; // NOSONAR

    // For error reporting only
    public static final String MIXINTYPE = "MixinType";

    public static final String PRIMARYTYPE = "PrimaryType";

    public static final String SCHEMA = "Schema";

    public static final String TYPE = "Type";
}
