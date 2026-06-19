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

public interface BaseModelDTO {
    Map<String, Object> toMap();

    default Map<String, Object> prepareMap(Map<String, ? extends BaseModelDTO> dto) {
        Map<String, Object> dtoToMap = new HashMap<>();
        if (dto == null)
            return null;
        dto.forEach((key, value) -> dtoToMap.put(key, value.toMap()));
        return dtoToMap;
    }

    static Map<String, FieldDTO> prepareFields(Object fieldObject) {
        if (fieldObject == null)
            return null;
        Map<String, FieldDTO> fieldDTOMap = new HashMap<>();
        if (fieldObject instanceof Map field) {
            Map<String, Object> fields = field;
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    if (!(value instanceof Map map)) {
                        FieldDTO fieldDTO = new FieldDTO(null, (String) value, null, null, false, null, null);
                        fieldDTOMap.put(entry.getKey(), fieldDTO);
                    } else {
                        FieldDTO fieldDTO = (FieldDTO) FieldDTO.toDTO(map);
                        fieldDTOMap.put(entry.getKey(), fieldDTO);
                    }
                }
            }
        }
        return fieldDTOMap;
    }

    static Map<String, Object> prepareList(Object listObject) {
        if (listObject instanceof Map listOfMap) {
            return listOfMap;
        } else {
            return Map.of(Constants.TYPE, listObject);
        }
    }
}
