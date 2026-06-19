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

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Field", description = "Represents a field.")
public record FieldDTO( //
        @Schema(
            deprecated = true,
            description = "Deprecated: default values are not supported and this property will be removed in a future version.",
            extensions = @Extension(properties = @ExtensionProperty(name = "setter-extra-annotation", value = "@Deprecated")))
        @JsonProperty("default") @JsonInclude(Include.NON_NULL) String defaultValue, //
        @JsonInclude(Include.NON_EMPTY) String type, //
        @JsonInclude(Include.NON_NULL) ConstraintDTO constraints, //
        @JsonProperty(Constants.EXTENDS) @JsonInclude(Include.NON_EMPTY) String extend, //
        @JsonInclude(Include.NON_DEFAULT) boolean fullTextIndexed, //
        @JsonInclude(Include.NON_NULL) ListDTO list, //
        @JsonInclude(Include.NON_NULL) Map<String, FieldDTO> fields) implements BaseModelDTO {

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (this.defaultValue() != null)
            map.put(Constants.DEFAULT, this.defaultValue());
        if (this.type() != null)
            map.put(Constants.TYPE, this.type());
        if (this.extend() != null)
            map.put(Constants.EXTENDS, this.extend());
        if (this.fullTextIndexed())
            map.put(Constants.FULL_TEXT_INDEXED, this.fullTextIndexed());
        if (this.fields() != null)
            map.put(Constants.FIELDS, prepareMap(this.fields()));
        if (this.constraints() != null) {
            map.put(Constants.CONSTRAINTS, this.constraints().toMap());
        }
        if (this.list() != null)
            map.put(Constants.LIST, this.list().toMap());
        return map;
    }

    public static BaseModelDTO toDTO(Map<String, Object> model) {
        String defaultValue = null;
        String type = null;
        ConstraintDTO constraintDTO = null;
        String extend = null;
        boolean fullTextIndexed = false;
        ListDTO listDTO = null;
        Map<String, FieldDTO> fields = null;
        if (model.get(Constants.DEFAULT) != null)
            defaultValue = (String) model.get(Constants.DEFAULT);
        if (model.get(Constants.TYPE) != null)
            type = (String) model.get(Constants.TYPE);
        if (model.get(Constants.CONSTRAINTS) != null)
            constraintDTO = (ConstraintDTO) ConstraintDTO.toDTO((Map<String, Object>) model.get("constraints"));
        if (model.get(Constants.EXTENDS) != null)
            extend = (String) model.get(Constants.EXTENDS);
        if (model.get(Constants.FULL_TEXT_INDEXED) != null)
            fullTextIndexed = (Boolean) model.get(Constants.FULL_TEXT_INDEXED);
        if (model.get(Constants.LIST) != null)
            listDTO = (ListDTO) ListDTO.toDTO(BaseModelDTO.prepareList(model.get(Constants.LIST)));
        if (model.get(Constants.FIELDS) != null)
            fields = BaseModelDTO.prepareFields(model.get(Constants.FIELDS));
        return new FieldDTO(defaultValue, type, constraintDTO, extend, fullTextIndexed, listDTO, fields);
    }
}
