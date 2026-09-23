/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static org.nuxeo.ecm.core.security.SecurityServiceImpl.CONFIG_SECURITY;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.nuxeo.ecm.core.api.local.WithUser;
import com.hyland.openarch.runtime.api.Framework;

import com.hyland.hxpr.config.ConfigurationService;

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

    /**
     * Reproduces a cluster invalidation lag: the policy is written straight to storage, so the persisted policies have
     * it while this node's local ones do not.
     */
    private void addPolicyToStorageOnly(String policyName, String policyProjectId) {
        ConfigurationService configurationService = Framework.getService(ConfigurationService.class);
        Map<String, Object> stored = new HashMap<>(
                configurationService.readConfiguration(REPO_ID, CONFIG_SECURITY).config());
        stored.put(policyName,
                Map.of("engine", "securitylevel", "mixin", "ClearanceElsewhere", "contentTypes",
                        List.of("TestDocument"), "permissions", List.of("Everything"), "userAttribute", "clearance",
                        "projectId", policyProjectId));
        configurationService.writeConfiguration(REPO_ID, CONFIG_SECURITY, stored);
    }

    /** The names of the policies as they are actually stored, hidden ones included. */
    private Set<String> storedPolicyNames() {
        return new TreeSet<>(Framework.getService(ConfigurationService.class)
                                      .readConfiguration(REPO_ID, CONFIG_SECURITY)
                                      .config()
                                      .keySet());
    }

    /**
     * A project write merges into the persisted policies, not the ones this node holds: this node's copy lags behind
     * while a cluster invalidation is in flight, and the write replaces the whole configuration, so merging into the
     * local copy would drop a policy another node has just added for a different project.
     */
    @Test
    void testPutProjectKeepsPoliciesOfOtherProjectsAddedElsewhere() throws Exception {
        resetAfterChange((String original) -> {
            addPolicyToStorageOnly("other project policy", "OTHERPROJ");

            checkResponseWithPayload(OK, PUT, REPO_ID, NEW_POLICY, API_PROJECT_PATH);

            // the other project's policy is still stored, and this node now reports it too
            assertTrue(storedPolicyNames().contains("other project policy"), storedPolicyNames().toString());
            assertTrue(getPolicies().has("other project policy"), getPolicies().toString());
        });
    }

    /** The policies this node reports, as the version-unaware API returns them. */
    private JSONObject getPolicies() throws Exception {
        var result = checkResponse(OK, GET, REPO_ID, SecurityPolicyAPI.API_PATH);
        return new JSONObject(result.andReturn().getResponse().getContentAsString());
    }
}
