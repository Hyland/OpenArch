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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OpenAPI shape of the {@code Resolver} schema, and nothing else.
 * <p>
 * {@link ResolverDTO} serializes through a {@link com.fasterxml.jackson.annotation.JsonValue}
 * {@code Map<String, Object>}, which swagger resolves as a free-form object; a {@code resolver}
 * property typed as {@link ResolverDTO} therefore documents as an inline map and generates as
 * {@code Map<String, Object>} in the SDK. To keep the generated SDK type
 * {@code com.hyland.hxpr.sdk.model.Resolver}, {@link ConstraintsDTO#resolver()} references this
 * record via {@link Schema#implementation()} so the OpenAPI description keeps a named {@code Resolver}
 * object with these named properties, independently of how {@link ResolverDTO} serializes at runtime.
 * <p>
 * This record is never instantiated; it exists purely to carry the schema. Its components mirror the
 * resolver-specific parameters the generated SDK exposes: {@code name} and {@code validation} plus
 * {@code vocabulary}, {@code store}, {@code type} and {@code repository}. Keep it in sync with the
 * documented resolver parameters.
 */
@Schema(name = "Resolver", description = "Represents a resolver. Beyond \"name\" and \"validation\","
        + " the properties are resolver-specific parameters, e.g. \"vocabulary\", \"repository\","
        + " \"store\" or \"type\".", type = "object")
record ResolverSchema( //
        @JsonInclude(Include.NON_EMPTY) String name, //
        boolean validation, //
        @JsonInclude(Include.NON_EMPTY) String vocabulary, //
        @JsonInclude(Include.NON_EMPTY) String store, //
        @JsonInclude(Include.NON_EMPTY) String type, //
        @JsonInclude(Include.NON_EMPTY) String repository) {
}
