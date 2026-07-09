/*
 * (C) Copyright 2021-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nelson Silva
 */
package org.nuxeo.rest.user;

import static java.util.Objects.requireNonNull;

import io.swagger.v3.oas.annotations.media.Schema;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.io.Serializable;
import java.util.Map;

/**
 * Simple user representation.
 */
@Schema(name = "User", title = "User", description = "Represents a user.", accessMode = Schema.AccessMode.READ_ONLY)
public record UserDTO( //
        @Schema(description = "UUID of the user.")
        String id, //
        @JsonInclude(Include.NON_NULL)
        @Schema(description = "First name of the user.")
        String firstName, //
        @JsonInclude(Include.NON_NULL)
        @Schema(description = "Last name of the user.")
        String lastName, //
        @JsonInclude(Include.NON_NULL)
        @Schema(description = "Login username of the user.")
        String username, //
        @JsonInclude(Include.NON_NULL)
        @Schema(description = "Email address of the user.")
        String email,
        @JsonInclude(Include.NON_EMPTY)
        Map<String, Serializable> properties) {

    public UserDTO {
        requireNonNull(id);
        properties = properties == null ? Map.of() : properties;
    }

    public UserDTO(NuxeoPrincipal user) {
        this(user.getId(), user.getFirstName(), user.getLastName(),
             user.getUsername(), user.getEmail(), user.getUserAttributes());
    }

    public UserDTO(String id) {
        this(id, null, null, null, null, Map.of());
    }
}
