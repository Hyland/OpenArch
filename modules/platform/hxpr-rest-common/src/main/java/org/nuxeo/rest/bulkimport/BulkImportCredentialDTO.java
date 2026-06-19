/*
 * (C) Copyright 2025 Hyland (https://hyland.com/) and others.
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
 *     Ananya Banerjee
 */

package org.nuxeo.rest.bulkimport;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Simple BulkImportCredential representation, useful for json serialization and swagger schema generation with the ReST
 * API.
 */
@Schema(name = "BulkImportCredential", title = "BulkImportCredential", description = "Represents a Bulk Import Job Credential Descriptor.", accessMode = AccessMode.READ_ONLY)
public record BulkImportCredentialDTO(String jobId,//
                                      @JsonInclude(JsonInclude.Include.NON_EMPTY) String awsAccessKeyId,//
                                      @JsonInclude(JsonInclude.Include.NON_EMPTY) String awsSecretAccessKey,//
                                      @JsonInclude(JsonInclude.Include.NON_EMPTY) String awsSessionToken) {
}
