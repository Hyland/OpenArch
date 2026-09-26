/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.rest.acl;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

public record ListEffectivePermissionsPerUserDTO(
        @JsonProperty(USERID_PROPERTY)
        String username,
        @JsonProperty(PERMISSIONS_PROPERTY)
        Set<String> permissions
) {

    public static final String USERID_PROPERTY = "userId";

    public static final String PERMISSIONS_PROPERTY = "userPermissions";
}
