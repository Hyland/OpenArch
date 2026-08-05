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
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@Schema(name = "Type", description = "Represents a type.")
public record TypeDTO( //
        @JsonInclude(Include.NON_EMPTY) String projectId, //
        @JsonInclude(Include.NON_NULL) Map<String, FieldDTO> fields, //
        @JsonInclude(Include.NON_NULL) ConstraintDTO constraints, //
        @JsonInclude(Include.NON_NULL) ListDTO list, //
        @JsonProperty("extends") @JsonInclude(Include.NON_EMPTY) String extend) implements BaseModelDTO {

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (this.fields() != null)
            map.put(Constants.FIELDS, prepareMap(this.fields()));
        if (this.extend() != null)
            map.put(Constants.EXTENDS, this.extend());
        if (this.list() != null)
            map.put(Constants.LIST, this.list().toMap());
        if (this.constraints() != null)
            map.put(Constants.CONSTRAINTS, this.constraints().toMap());
        if (this.projectId() != null)
            map.put(Constants.PROJECT_ID, this.projectId());
        return map;
    }

    public static BaseModelDTO toDTO(Map<String, Object> type) {
        Map<String, FieldDTO> fields = BaseModelDTO.prepareFields(type.get(Constants.FIELDS));
        String extend = (String) type.get(Constants.EXTENDS);
        String projectIdent = null;
        ListDTO list = null;
        ConstraintDTO constraintDTO = null;
        if (type.get(Constants.PROJECT_ID) != null)
            projectIdent = (String) type.get(Constants.PROJECT_ID);
        if (type.get(Constants.LIST) != null)
            list = (ListDTO) ListDTO.toDTO(BaseModelDTO.prepareList(type.get(Constants.LIST)));
        if (type.get(Constants.CONSTRAINTS) != null)
            constraintDTO = (ConstraintDTO) ConstraintDTO.toDTO((Map<String, Object>) type.get(Constants.CONSTRAINTS));
        return new TypeDTO(projectIdent, fields, constraintDTO, list, extend);
    }

    public TypeDTO withProjectId(String projectId) {
        return new TypeDTO(projectId, fields, constraints, list, extend);
    }

}
