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
package org.nuxeo.rest.copy;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple Copy API command.
 *
 */
@Schema(name = "CopyCommand", title = "Copy Body", description = "Represents Copy API's HTTP Post body.")
public record CopyDTO(
        @Schema(description = "Name of the document after copying.  Example: \"name\": \"Copied file name\"",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("name")
        String name,
        @Schema(description = "Target parent id where the document will be copied.  Example: \"targetParentId\": \"097d50d7-f839-440a-b72c-f2a51ea214b7\"",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("targetParentId")
        String targetParentId) {
}
