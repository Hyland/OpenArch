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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A schema: a named set of fields sharing a prefix.
 * <p>
 * {@link #prefix} is informational on the way in: the schema layer re-derives it from the field
 * names, so setting it on a request has no effect.
 */
@Schema(name = "Schema", description = "Represents a schema.")
public record SchemaDTO( //
        @JsonInclude(Include.NON_NULL) String projectId, //
        Map<String, FieldDTO> fields, //
        @JsonInclude(Include.NON_DEFAULT) boolean versionWritable, //
        @Schema(description = "Field name prefix. Read-only: it is derived from the field names.") //
        @JsonInclude(Include.NON_NULL) String prefix) {

    public SchemaDTO withProjectId(String newProjectId) {
        return new SchemaDTO(newProjectId, fields, versionWritable, prefix);
    }
}
