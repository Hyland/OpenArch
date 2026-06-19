/*
 * (C) Copyright 2022-2024 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Abdul Mohammed <abdul.mohammed@hyland.com>
 */
package org.nuxeo.rest.acl;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.rest.user.GroupDTO;
import org.nuxeo.rest.user.UserDTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Schema(name = "ACE", title = "ACE", description = "REST representation of ACEs.")
public record ACEDTO(
        @JsonInclude(Include.NON_NULL)
        @Schema(description = "Name of the user who created the ACE.")
        String creator,
        @Schema(description = "The permission assigned to members of the ACE.")
        String permission,
        @Schema(description = "True/false indicator of whether the specified permission has been granted to the ACE members.")
        Boolean granted,
        @JsonInclude(Include.NON_NULL)
        UserDTO user,
        @JsonInclude(Include.NON_NULL)
        GroupDTO group,
        @JsonInclude(Include.NON_NULL)
        @Schema(format = "date-time", type = "string", description = "The date and time when the ACE rule stops being applied.")
        Instant end,
        @JsonInclude(Include.NON_NULL)
        @Schema(format = "date-time", type = "string", description = "The date and time when the ACE rule starts being applied.")
        Instant begin,
        @Schema(enumAsRef = true, description = "The current status of the application of the ACE rule.")
        ACE.Status status) {
}
