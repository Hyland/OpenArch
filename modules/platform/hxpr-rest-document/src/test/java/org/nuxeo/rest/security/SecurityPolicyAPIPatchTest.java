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
package org.nuxeo.rest.security;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.PUT;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.hyland.openarch.ecm.core.api.local.WithUser;

import jakarta.ws.rs.core.MediaType;

/**
 * Tests the patching of security policy configurations via the REST API.
 */
class SecurityPolicyAPIPatchTest extends AbstractSecurityPolicyAPITest {

    @WithUser(TEST_ANONYMOUS_USER)
    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPatchUnauthenticated(String queryParam) throws Exception {
        checkUnauthorizedPatchWithCsrf(SecurityPolicyAPI.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(ALT_USER)
    void testPatchUnauthorized(String queryParam) throws Exception {
        checkResponseWithPayload(UNAUTHORIZED, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, "[]",
                SecurityPolicyAPI.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPatchBadRepo(String queryParam) throws Exception {
        checkResponseWithPayload(NOT_FOUND, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, BAD_REPO_ID, "[]",
                SecurityPolicyAPI.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPatchInvalidJson(String queryParam) throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, NOT_JSON,
                SecurityPolicyAPI.API_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPatch(String queryParam) throws Exception {
        resetAfterChange((String original) -> {
            // Need to add something to update, as all the original policies are immutable, so have been hidden
            var result = checkResponseWithPayload(OK, PUT, REPO_ID, NEW_POLICY, SecurityPolicyAPI.API_PATH);
            checkResponseBodyEquals(NEW_POLICY, result);

            String patchBody = """
                    [
                      { "op": "replace", "path": "/security clearance/userAttribute", "value": "user_clearance" }
                    ]
                    """;

            if (queryParam.equals(PARAM_VALIDATE_ONLY_TRUE)) {
                result = checkResponseWithPayload(NO_CONTENT, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, patchBody,
                        SecurityPolicyAPI.API_PATH + queryParam);
                assertEquals("", result.andReturn().getResponse().getContentAsString());
            } else {
                result = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, patchBody,
                        SecurityPolicyAPI.API_PATH + queryParam);
                checkResponseBodyEquals(UPDATED_USER_ATTRIBUTE, result);
            }
        });
    }
}
