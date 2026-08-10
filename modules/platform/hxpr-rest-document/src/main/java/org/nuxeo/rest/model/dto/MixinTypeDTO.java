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

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Schema(name = "MixinType", description = "Represents a mixin.")
public record MixinTypeDTO( //
        @JsonInclude(Include.NON_NULL) String projectId, //
        @JsonInclude(Include.NON_DEFAULT) boolean noPerDocumentQuery, //
        @JsonInclude(Include.NON_EMPTY) List<String> schemas) implements BaseModelDTO {

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> mixinMap = new HashMap<>();
        if (this.noPerDocumentQuery())
            mixinMap.put(Constants.NO_PER_DOCUMENT_QUERY, this.noPerDocumentQuery());
        if (this.projectId() != null)
            mixinMap.put(Constants.PROJECT_ID, this.projectId());
        if (this.schemas() != null)
            mixinMap.put(Constants.SCHEMAS, this.schemas());
        return mixinMap;
    }

    public static BaseModelDTO toDTO(Map<String, Object> model) {
        boolean noPerDocQuery = false;
        if (model.get(Constants.NO_PER_DOCUMENT_QUERY) != null)
            noPerDocQuery = Boolean.valueOf(model.get(Constants.NO_PER_DOCUMENT_QUERY).toString());
        String projectId = (String) model.get(Constants.PROJECT_ID);
        List<String> schemas = (List<String>) model.get(Constants.SCHEMAS);
        return new MixinTypeDTO(projectId, noPerDocQuery, schemas);
    }

    public MixinTypeDTO withProjectId(String projectId) {
        return new MixinTypeDTO(projectId, noPerDocumentQuery, schemas);
    }
}
