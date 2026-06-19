/*
 * (C) Copyright 2022-2024 Hyland (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Saumit Mandal
 */
package org.nuxeo.rest.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@Schema(name = "List", description = "Represents a list.")
public record ListDTO( //
        @JsonInclude(Include.NON_NULL) String type, //
        @JsonInclude(Include.NON_NULL) ConstraintDTO constraints, //
        @JsonProperty(Constants.EXTENDS) @JsonInclude(Include.NON_NULL) String extend) implements BaseModelDTO {

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (this.type() != null)
            map.put(Constants.TYPE, this.type());
        if (this.constraints() != null)
            map.put(Constants.CONSTRAINTS, this.constraints().toMap());
        if (this.extend() != null)
            map.put(Constants.EXTENDS, this.extend());
        return map;
    }

    public static BaseModelDTO toDTO(Map<String, Object> model) {
        String type = (String) model.get(Constants.TYPE);
        ConstraintDTO constraintDTO = (ConstraintDTO) ConstraintDTO.toDTO(
                (Map<String, Object>) model.get(Constants.CONSTRAINTS));
        String extend = (String) model.get(Constants.EXTENDS);
        return new ListDTO(type, constraintDTO, extend);
    }
}
