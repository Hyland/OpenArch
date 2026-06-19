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

@Schema(name = "Schema", description = "Represents a schema.")
public record SchemaDTO( //
        @JsonInclude(Include.NON_NULL) String projectId, //
        Map<String, FieldDTO> fields, //
        @JsonInclude(Include.NON_DEFAULT) boolean versionWritable, //
        String prefix) implements BaseModelDTO {
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (this.fields() != null)
            map.put(Constants.FIELDS, prepareMap(this.fields()));
        if (this.versionWritable())
            map.put(Constants.VERSION_WRITABLE, this.versionWritable());
        if (this.prefix() != null)
            map.put(Constants.PREFIX, this.prefix());
        if (this.projectId() != null)
            map.put(Constants.PROJECT_ID, this.projectId());
        return map;
    }

    public static BaseModelDTO toDTO(Map<String, Object> schema) {
        Map<String, FieldDTO> fields = BaseModelDTO.prepareFields(schema.get(Constants.FIELDS));
        String prefix = null;
        String projectId = null;
        boolean versionWritable = false;
        if (schema.get(Constants.PREFIX) != null)
            prefix = schema.get(Constants.PREFIX).toString();
        if (schema.get(Constants.PROJECT_ID) != null)
            projectId = (String) schema.get(Constants.PROJECT_ID);
        if (schema.get(Constants.VERSION_WRITABLE) != null)
            versionWritable = Boolean.parseBoolean(schema.get(Constants.VERSION_WRITABLE).toString());
        return new SchemaDTO(projectId, fields, versionWritable, prefix);
    }

    public SchemaDTO withProjectId(String projectId) {
        return new SchemaDTO(projectId, fields, versionWritable, prefix);
    }
}
