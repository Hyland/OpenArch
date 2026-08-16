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

import io.swagger.v3.oas.annotations.media.Schema;

/** A mixin type: a named set of schemas that can be applied to a primary type or a document. */
@Schema(name = "MixinType", description = "Represents a mixin.")
public record MixinTypeDTO( //
        @JsonInclude(Include.NON_NULL) String projectId, //
        @JsonInclude(Include.NON_DEFAULT) boolean noPerDocumentQuery, //
        @JsonInclude(Include.NON_EMPTY) List<String> schemas) {

    public MixinTypeDTO withProjectId(String newProjectId) {
        return new MixinTypeDTO(newProjectId, noPerDocumentQuery, schemas);
    }
}
