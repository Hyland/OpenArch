/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.security;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The security policies together with their optimistic-locking version, as exchanged by the v2 API.
 * <p>
 * This envelope exists because the v1 API has the policies at the root of the payload, keyed by policy name. A
 * {@code version} property there would be ambiguous — it could not be told apart from a policy actually named
 * {@code version} — so v2 nests them under {@link #policies} instead. The v1 shape is untouched.
 *
 * @param version the optimistic-locking version: the value read before computing an update, sent back with it, and
 *            returned (incremented) once the update has been stored
 * @param policies the security policies, keyed by policy name
 */
@Schema(name = "SecurityPolicies", description = "Represents the security policies and their version.")
public record SecurityPoliciesDTO( //
        @Schema(description = "Optimistic-locking version, present on the v2 API only.") //
        @JsonInclude(Include.NON_NULL) Long version, //
        Map<String, SecurityPolicyConfigDTO> policies) {

    /** The same policies carrying the given optimistic-locking {@code version}. */
    public static SecurityPoliciesDTO of(Map<String, SecurityPolicyConfigDTO> policies, long version) {
        return new SecurityPoliciesDTO(version, policies);
    }

}
