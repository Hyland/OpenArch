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
package com.hyland.openarch.rest.security;

import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.OK;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.hyland.openarch.ecm.core.security.SecurityServiceImpl.CONFIG_SECURITY;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_CONFIG_VERSION;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_ID;
import static com.hyland.openarch.rest.security.SecurityPolicyAPI.API_PATH_V2;
import static com.hyland.openarch.rest.utils.Headers.HXCS_REPOSITORY;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.bson.Document;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.rest.utils.Versions;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

/**
 * Security Policy REST API v2 tests, focused on the optimistic-locking behaviour.
 * <p>
 * The v2 payload is an envelope, {@code { "version": n, "policies": { … } }}, so that the version cannot be confused
 * with a policy of that name; the v1 payload keeps the policies at the root.
 */
class SecurityPolicyAPIV2Test extends AbstractSecurityPolicyAPITest {

    @RuntimeInject
    protected CoreFeature coreFeature;

    @BeforeEach
    void setUpV2() throws Exception {
        // optimistic locking is only enforced by the MongoDB persistence engine, see ModelAPIV2Test
        assumeTrue(coreFeature.getStorageConfiguration().isDBSMongoDB(),
                "Optimistic locking is only enforced on the MongoDB backend");
        resetPolicies();
    }

    @AfterEach
    void tearDownV2() throws Exception {
        resetPolicies();
    }

    /**
     * Brings the policies back to none. The policies are persisted and shared, so each test both starts and leaves from
     * that state: applying {@link #NEW_POLICY} is then always a change, whatever ran before.
     */
    private void resetPolicies() throws Exception {
        long version = getPolicies().getLong("version");
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), envelope(version, ORIGINAL_POLICIES), API_PATH_V2);
    }

    private HttpHeaders v2Headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HXCS_REPOSITORY, REPO_ID);
        headers.set(ACCEPT, Versions.APP_HXCS_JSON_V2);
        return headers;
    }

    private HttpHeaders v2PutHeaders() {
        HttpHeaders headers = v2Headers();
        headers.set(CONTENT_TYPE, APPLICATION_JSON);
        return headers;
    }

    private JSONObject getPolicies() throws Exception {
        ResultActions result = checkResponse(OK, GET, v2Headers(), API_PATH_V2);
        return new JSONObject(result.andReturn().getResponse().getContentAsString());
    }

    /** The v2 envelope carrying {@link #NEW_POLICY} and the given version. */
    private static String envelope(long version, String policies) throws Exception {
        return new JSONObject().put("version", version).put("policies", new JSONObject(policies)).toString();
    }

    @Test
    void testGetReturnsVersionAndPolicies() throws Exception {
        checkResponse(OK, GET, v2Headers(), API_PATH_V2).andExpect(jsonPath("$.version").exists())
                                                        .andExpect(jsonPath("$.policies").exists());
    }

    @Test
    void testCleanUpdateIncrementsVersion() throws Exception {
        long version = getPolicies().getLong("version");
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), envelope(version, NEW_POLICY), API_PATH_V2)
                .andExpect(jsonPath("$.version").value(version + 1))
                .andExpect(jsonPath("$.policies['security clearance']").exists());
        // and the new version is what a fresh read reports
        checkResponse(OK, GET, v2Headers(), API_PATH_V2).andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    void testUnchangedUpdateIsNoOpAndKeepsVersion() throws Exception {
        long version = getPolicies().getLong("version");
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), envelope(version, NEW_POLICY), API_PATH_V2)
                .andExpect(jsonPath("$.version").value(version + 1));
        // re-applying the same policies changes nothing, so no write happens and the version stays put
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), envelope(version + 1, NEW_POLICY), API_PATH_V2)
                .andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    void testStaleVersionConflicts() throws Exception {
        long staleVersion = getPolicies().getLong("version");
        checkResponseWithPayload(OK, PUT, v2PutHeaders(), envelope(staleVersion, NEW_POLICY), API_PATH_V2)
                .andExpect(jsonPath("$.version").value(staleVersion + 1));
        // a write still carrying the now-stale version is rejected, even though it sends the same policies
        checkResponseWithPayload(CONFLICT, PUT, v2PutHeaders(), envelope(staleVersion, NEW_POLICY), API_PATH_V2);
    }

    @Test
    void testMissingVersionIsBadRequest() throws Exception {
        String noVersion = new JSONObject().put("policies", new JSONObject(NEW_POLICY)).toString();
        checkResponseWithPayload(BAD_REQUEST, PUT, v2PutHeaders(), noVersion, API_PATH_V2);
        // a validate-only request describes the write it is checking, so it carries a version too
        checkResponseWithPayload(BAD_REQUEST, PUT, v2PutHeaders(), noVersion,
                API_PATH_V2 + PARAM_VALIDATE_ONLY_TRUE);
    }

    /**
     * Reproduces a cluster invalidation lag: the policy is added straight to storage, so the persisted policies (which
     * the version refers to) have it while this node's local policies do not.
     */
    private void addPolicyToStorageOnly(String policyName, String policyProjectId, long newVersion) {
        Document policy = new Document("engine", "securitylevel").append("mixin", "Clearance" + policyName)
                                                                .append("contentTypes", List.of("TestDocument"))
                                                                .append("permissions", List.of("Everything"))
                                                                .append("userAttribute", "clearance")
                                                                .append("projectId", policyProjectId);
        coreFeature.getStorageConfiguration()
                   .runWithMongoDatabase(db -> db.getCollection(
                           coreFeature.getStorageConfiguration().getRepositoryName() + ".config")
                                                 .updateOne(Filters.eq(MONGODB_ID, CONFIG_SECURITY),
                                                         Updates.combine(Updates.set(policyName, policy),
                                                                 Updates.set(MONGODB_CONFIG_VERSION, newVersion))));
    }

    /** The names of the policies as they are actually stored, hidden ones included. */
    private Set<String> storedPolicyNames() {
        Document[] doc = new Document[1];
        coreFeature.getStorageConfiguration()
                   .runWithMongoDatabase(db -> doc[0] = db.getCollection(
                           coreFeature.getStorageConfiguration().getRepositoryName() + ".config")
                                                          .find(Filters.eq(MONGODB_ID, CONFIG_SECURITY))
                                                          .first());
        return new TreeSet<>(doc[0].keySet());
    }

    /**
     * The v2 read reports the persisted policies and the version together, so the version always describes the policies
     * returned. Pairing the policies held locally with the persisted version would hand out a version describing content
     * the caller never saw, which the caller would then overwrite.
     */
    @Test
    void testGetReportsPersistedPoliciesAndVersionTogether() throws Exception {
        long version = getPolicies().getLong("version");
        addPolicyToStorageOnly("other policy", "OTHERPROJ", version + 1);

        JSONObject read = getPolicies();
        assertEquals(version + 1, read.getLong("version"));
        assertTrue(read.getJSONObject("policies").has("other policy"), read.toString());
    }

    /**
     * A v2 project write merges into the persisted policies, not the ones this node holds. Merging into a stale local
     * copy would drop another project's policy from the full configuration written, under a version that matches and so
     * raises no conflict.
     */
    @Test
    void testPutForProjectV2KeepsPoliciesOfOtherProjectsAddedElsewhere() throws Exception {
        long version = getPolicies().getLong("version");
        addPolicyToStorageOnly("other policy", "OTHERPROJ", version + 1);

        checkResponseWithPayload(OK, PUT, v2PutHeaders(), envelope(version + 1, NEW_POLICY),
                API_PATH_V2 + PROJECT_PF_00000).andExpect(jsonPath("$.version").value(version + 2));

        // the other project's policy is still stored, and is still reported
        assertTrue(storedPolicyNames().contains("other policy"), storedPolicyNames().toString());
        assertTrue(getPolicies().getJSONObject("policies").has("other policy"), getPolicies().toString());
    }

    @Test
    void testV1ShapeIsUnchanged() throws Exception {
        // the v1 payload keeps the policies at the root, with no version anywhere
        ResultActions result = checkResponse(OK, GET, REPO_ID, SecurityPolicyAPI.API_PATH);
        JSONObject v1 = new JSONObject(result.andReturn().getResponse().getContentAsString());
        assertFalse(v1.has("version"), v1.toString());
        assertFalse(v1.has("policies"), v1.toString());
    }
}
