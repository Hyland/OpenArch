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
package org.nuxeo.ecm.core.schema.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A named type: a simple type ({@link #extend} plus {@link #constraints}), a list type
 * ({@link #list}) or a complex type ({@link #fields}).
 * <p>
 * Unlike {@link FieldDTO}, a named type always spells its supertype {@code extends}, never
 * {@code type}, in both the persisted and the REST shape.
 */
@Schema(name = "Type", description = "Represents a type.")
public record TypeDTO( //
        @JsonInclude(Include.NON_EMPTY) String projectId, //
        @JsonInclude(Include.NON_NULL) Map<String, FieldDTO> fields, //
        @JsonInclude(Include.NON_NULL) ConstraintsDTO constraints, //
        @JsonInclude(Include.NON_NULL) FieldDTO list, //
        @JsonProperty("extends") @JsonInclude(Include.NON_EMPTY) String extend) {

    public TypeDTO withProjectId(String newProjectId) {
        return new TypeDTO(newProjectId, fields, constraints, list, extend);
    }
}
