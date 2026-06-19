/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
package org.nuxeo.rest.schema;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Added when switching from Quarkus to Spring in order to maintain the OpenApi output for
 * {@code java.time.Instant} properties. Note the type with Quarkus was {@code string} but is now {@code object}
 * despite the following annotation. It is probably now correct.
 */
@Schema(name = "Instant", example = "2022-03-10T16:15:50.000Z", format = "date-time", type = "string")
public record SchemaInstant() {
}
