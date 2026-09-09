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
package com.hyland.openarch.rest.user;


import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

import com.hyland.openarch.ecm.core.api.RepositoryGroup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Simple group representation.
 */
@Schema(name = "Group", title = "Group", description = "Represents a group.", accessMode = Schema.AccessMode.READ_ONLY)
public record GroupDTO(
        @Schema(description = "UUID of the group.")
        String id,
        @JsonInclude(Include.NON_NULL)
        @Schema(description = "Name of the group.")
        String name
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public GroupDTO(RepositoryGroup group) {
        this(group.getId(), group.getName());
    }

    public GroupDTO(String groupId) {
        this(groupId, null);
    }
}
