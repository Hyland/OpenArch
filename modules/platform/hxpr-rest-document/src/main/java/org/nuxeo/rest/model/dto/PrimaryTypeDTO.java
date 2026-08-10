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
import com.fasterxml.jackson.annotation.JsonProperty;

@Schema(name = "PrimaryType", description = "Represents a primary type.")
public record PrimaryTypeDTO( //
        @JsonInclude(Include.NON_EMPTY) String projectId, //
        @JsonInclude(Include.NON_DEFAULT) boolean special, // unused
        @JsonProperty("extends") @JsonInclude(Include.NON_EMPTY) String extend, //
        @JsonInclude(Include.NON_NULL) List<String> mixins, //
        @JsonInclude(Include.NON_NULL) List<String> schemas, //
        @JsonInclude(Include.NON_NULL) List<String> subtypes) implements BaseModelDTO {

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(Constants.EXTENDS, this.extend());
        map.put(Constants.MIXINS, this.mixins());
        map.put(Constants.SCHEMAS, this.schemas());
        map.put(Constants.SUBTYPES, this.subtypes());
        if (this.projectId() != null)
            map.put(Constants.PROJECT_ID, this.projectId());
        return map;
    }

    public static BaseModelDTO toDTO(Map<String, Object> primaryType) {
        List<String> mixins = null;
        String extend = null;
        List<String> schemas = null;
        List<String> subtypes = null;
        String projectId = null;
        boolean special = false; // unused
        mixins = (List<String>) primaryType.get(Constants.MIXINS);
        extend = (String) primaryType.get(Constants.EXTENDS);
        schemas = (List<String>) primaryType.get(Constants.SCHEMAS);
        subtypes = (List<String>) primaryType.get(Constants.SUBTYPES);
        if (primaryType.get(Constants.PROJECT_ID) != null)
            projectId = (String) primaryType.get(Constants.PROJECT_ID);
        return new PrimaryTypeDTO(projectId, special, extend, mixins, schemas, subtypes);
    }

    public PrimaryTypeDTO withProjectId(String projectId) {
        return new PrimaryTypeDTO(projectId, special, extend, mixins, schemas, subtypes);
    }
}
