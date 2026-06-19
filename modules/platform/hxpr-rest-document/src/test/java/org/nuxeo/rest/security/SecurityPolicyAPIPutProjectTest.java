/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.rest.security;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static org.springframework.http.HttpMethod.PUT;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.nuxeo.ecm.core.api.local.WithUser;

class SecurityPolicyAPIPutProjectTest extends AbstractSecurityPolicyAPITest {

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(TEST_ANONYMOUS_USER)
    void testPutProjectUnauthenticated(String queryParam) throws Exception {
        checkUnauthorizedPutWithCsrf(API_PROJECT_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    @WithUser(ALT_USER)
    void testPutProjectUnauthorized(String queryParam) throws Exception {
        checkResponseWithPayload(UNAUTHORIZED, PUT, REPO_ID, "{}", API_PROJECT_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutProjectBadRepo(String queryParam) throws Exception {
        checkResponseWithPayload(NOT_FOUND, PUT, BAD_REPO_ID, "{}", API_PROJECT_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutProjectInvalidJson(String queryParam) throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PUT, BAD_REPO_ID, NOT_JSON, API_PROJECT_PATH + queryParam);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutProject(String queryParam) throws Exception {
        resetAfterChange((String original) -> {
            if (queryParam.equals(PARAM_VALIDATE_ONLY_TRUE)) {
                var result = checkResponseWithPayload(NO_CONTENT, PUT, REPO_ID, NEW_POLICY,
                        API_PROJECT_PATH + queryParam);
                assertEquals("", result.andReturn().getResponse().getContentAsString());
                assertNoChange(ORIGINAL_POLICIES);
            } else {
                var result = checkResponseWithPayload(OK, PUT, REPO_ID, NEW_POLICY, API_PROJECT_PATH + queryParam);
                checkResponseBodyEquals(UPDATED_PROJECT, result);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "", PARAM_VALIDATE_ONLY_FALSE, PARAM_VALIDATE_ONLY_TRUE })
    void testPutProjectReturnsSingleProjectPolicies(String queryParam) throws Exception {
        // Tests that only policies with the projectId are returned unlike a simple PUT
        // See testPutReturnsMultipleProjectPolicies(...) for comparison
        resetAfterChange((String original) -> {
            // Change the original config, so it contains policies from multiple projects.
            var result = checkResponseWithPayload(OK, PUT, REPO_ID, NEW_PROJECT_POLICIES, SecurityPolicyAPI.API_PATH);
            checkResponseBodyEquals(NEW_PROJECT_POLICIES, result);

            if (queryParam.equals(PARAM_VALIDATE_ONLY_TRUE)) {
                result = checkResponseWithPayload(NO_CONTENT, PUT, REPO_ID, NEW_POLICY, API_PROJECT_PATH + queryParam);
                assertEquals("", result.andReturn().getResponse().getContentAsString());
                assertNoChange(NEW_PROJECT_POLICIES);
            } else {
                result = checkResponseWithPayload(OK, PUT, REPO_ID, NEW_POLICY, API_PROJECT_PATH + queryParam);
                checkResponseBodyEquals(UPDATED_PROJECT, result);
            }
        });
    }
}
