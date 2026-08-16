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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** A primary (document) type. */
@Schema(name = "PrimaryType", description = "Represents a primary type.")
public record PrimaryTypeDTO( //
        @JsonInclude(Include.NON_EMPTY) String projectId, //
        @Schema( //
                deprecated = true, //
                description = "Deprecated: the special flag is obsolete and will be removed in a future version.", //
                extensions = @Extension(properties = @ExtensionProperty(name = "setter-extra-annotation", value = "@Deprecated"))) //
        @JsonInclude(Include.NON_DEFAULT) boolean special, //
        @JsonProperty("extends") @JsonInclude(Include.NON_EMPTY) String extend, //
        @JsonInclude(Include.NON_NULL) List<String> schemas, //
        @JsonInclude(Include.NON_NULL) List<String> mixins, //
        @JsonInclude(Include.NON_NULL) List<String> subtypes) {

    public PrimaryTypeDTO(String projectId, String extend, List<String> schemas, List<String> mixins,
            List<String> subtypes) {
        this(projectId, false, extend, schemas, mixins, subtypes);
    }

    public PrimaryTypeDTO withProjectId(String newProjectId) {
        return new PrimaryTypeDTO(newProjectId, extend, schemas, mixins, subtypes);
    }
}
