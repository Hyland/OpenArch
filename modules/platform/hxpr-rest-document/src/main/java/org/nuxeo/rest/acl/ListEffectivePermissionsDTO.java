/*
 * (C) Copyright 2024 Hyland (http://www.hyland.com/) and others.
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
 *     Gildas Lefevre
 */
package org.nuxeo.rest.acl;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ListEffectivePermissionsDTO(

        @JsonProperty(SYS_ID)
        String id,

        @JsonProperty(FULL_EFFECTIVE_PERMISSIONS)
        List<ListEffectivePermissionsPerUserDTO> fullEffectivePermissions
) {

    public static final String FULL_EFFECTIVE_PERMISSIONS = "fullEffectivePermissions";
}
