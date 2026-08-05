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

@Schema(name = "Model", description = "Represents a model.")
public record ModelDTO( //
        @JsonInclude(Include.NON_NULL) Map<String, SchemaDTO> schemas, //
        @JsonInclude(Include.NON_NULL) Map<String, TypeDTO> types, //
        @JsonInclude(Include.NON_NULL) Map<String, MixinTypeDTO> mixinTypes, //
        @JsonInclude(Include.NON_NULL) Map<String, PrimaryTypeDTO> primaryTypes) implements BaseModelDTO {

    public ModelDTO(Map<String, SchemaDTO> schemas, Map<String, TypeDTO> types, Map<String, MixinTypeDTO> mixinTypes,
            Map<String, PrimaryTypeDTO> primaryTypes) {
        this.schemas = schemas == null ? new HashMap<>() : schemas;
        this.types = types == null ? new HashMap<>() : types;
        this.mixinTypes = mixinTypes == null ? new HashMap<>() : mixinTypes;
        this.primaryTypes = primaryTypes == null ? new HashMap<>() : primaryTypes;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(Constants.MIXIN_TYPES, prepareMap(this.mixinTypes()));
        map.put(Constants.PRIMARY_TYPES, prepareMap(this.primaryTypes()));
        map.put(Constants.TYPES, prepareMap(this.types()));
        map.put(Constants.SCHEMAS, prepareMap(this.schemas()));
        return map;
    }

    public static ModelDTO toDTO(Map<String, Object> model) {
        Map<String, Object> mixinTypesFromMap = (Map<String, Object>) model.get(Constants.MIXIN_TYPES);
        Map<String, MixinTypeDTO> mixinTypesMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : mixinTypesFromMap.entrySet()) {
            MixinTypeDTO mixinTypeDTO = (MixinTypeDTO) MixinTypeDTO.toDTO((Map<String, Object>) entry.getValue());
            mixinTypesMap.put(entry.getKey(), mixinTypeDTO);
        }
        Object primaryTypeObject = model.get(Constants.PRIMARY_TYPES);
        Map<String, PrimaryTypeDTO> primaryTypeDTOMap = new HashMap<>();
        if (primaryTypeObject instanceof Map primaryMap) {
            Map<String, Object> primaryTypesFromMap = primaryMap;
            for (Map.Entry<String, Object> entry : primaryTypesFromMap.entrySet()) {
                PrimaryTypeDTO primaryTypeDTO = (PrimaryTypeDTO) PrimaryTypeDTO.toDTO((Map<String, Object>) entry.getValue());
                primaryTypeDTOMap.put(entry.getKey(), primaryTypeDTO);
            }
        }

        Map<String, Object> typesFromMap = (Map<String, Object>) model.get(Constants.TYPES);
        Map<String, TypeDTO> typeDTOMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : typesFromMap.entrySet()) {
            TypeDTO typeDTO = (TypeDTO) TypeDTO.toDTO((Map<String, Object>) entry.getValue());
            typeDTOMap.put(entry.getKey(), typeDTO);
        }

        Map<String, Object> schemasFromMap = (Map<String, Object>) model.get(Constants.SCHEMAS);
        Map<String, SchemaDTO> schemaDTOMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : schemasFromMap.entrySet()) {
            SchemaDTO schemaDTO = (SchemaDTO) SchemaDTO.toDTO((Map<String, Object>) entry.getValue());
            schemaDTOMap.put(entry.getKey(), schemaDTO);
        }
        return new ModelDTO(schemaDTOMap, typeDTOMap, mixinTypesMap, primaryTypeDTOMap);
    }

}
