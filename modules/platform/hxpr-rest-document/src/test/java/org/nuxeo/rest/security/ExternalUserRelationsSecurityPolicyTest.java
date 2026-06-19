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
package org.nuxeo.rest.security;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.nuxeo.ecm.core.security.ExternalUserRelationsSecurityPolicy.EXTERNAL_USER_RELATIONS_ENGINE_NAME;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;

import java.time.Instant;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.runtime.test.runner.Deploy;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = { SecurityPolicyAPI.class, ExternalUserRelationsSecurityPolicyAPI.class,
        SecurityPolicyManager.class, SecurityPolicyConfigMapper.class })
@Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-external-user-relations-security-policy.xml")
class ExternalUserRelationsSecurityPolicyTest extends AbstractAPITest {

    private static final String POLICY = "ExternalUserRelationsPolicy";

    private static final String SECURITY_POLICY_PAYLOAD = """
            {
              "%s": {
                "engine": "%s",
                "mixin": "Owned",
                "contentTypes": ["OwnedFile"],
                "permissions": ["Everything"],
                "relations": ["Manager", "Admin"],
                "userAttribute": "externalUserId"
              }
            }""".formatted(POLICY, EXTERNAL_USER_RELATIONS_ENGINE_NAME);

    private static final String RELATIONS_EMPTY_PAYLOAD = """
            {
              "users": []
            }""";

    private static final String RELATIONS_PAYLOAD = """
            {
              "users": [
                {
                  "id": "alice",
                  "relations": {
                    "Manager": ["bob"],
                    "Admin": ["charlie"]
                  }
                },
                {
                  "id": "bob",
                  "relations": {
                    "Manager": ["charlie"],
                    "Admin": ["charlie"]
                  }
                }
              ]
            }""";

    @AfterEach
    void afterEach() throws Exception {
        var response = checkResponse(OK, GET, REPO_ID, SecurityPolicyAPI.API_PATH).andReturn().getResponse();
        if (response.getContentAsString().contains(POLICY)) {
            // reset policy relations
            checkResponseWithPayload(NO_CONTENT, PUT, REPO_ID, RELATIONS_EMPTY_PAYLOAD, getRelationsPath());
            // reset policies
            checkResponseWithPayload(OK, PUT, REPO_ID, "{}", SecurityPolicyAPI.API_PATH);
        }
    }

    @Test
    void testGetMissing() throws Exception {
        checkResponse(NOT_FOUND, GET, REPO_ID, getRelationsPath());
    }

    @Test
    void testPut() throws Exception {
        checkResponseWithPayload(OK, PUT, REPO_ID, SECURITY_POLICY_PAYLOAD, SecurityPolicyAPI.API_PATH);
        checkResponseBodyEquals(RELATIONS_EMPTY_PAYLOAD, checkResponse(OK, GET, REPO_ID, getRelationsPath()));
        checkResponseWithPayload(NO_CONTENT, PUT, REPO_ID, RELATIONS_PAYLOAD, getRelationsPath());
        checkResponseBodyEquals(RELATIONS_PAYLOAD, checkResponse(OK, GET, REPO_ID, getRelationsPath()));
    }

    @Test
    void testPutInvalidPayload() throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, "{", getRelationsPath());
    }

    @Test
    void testPutMissing() throws Exception {
        checkResponseWithPayload(NOT_FOUND, PUT, REPO_ID, RELATIONS_EMPTY_PAYLOAD, getRelationsPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"""
            {
              "users": [
                {
                  "id": "alice",
                  "relations": null
                }
              ]
            }""", """
            {
              "users": [
                {
                  "id": "alice",
                  "relations": [null]
                }
              ]
            }""", """
            {
              "users": [
                {
                  "id": "alice",
                  "relations": {
                    "Manager": null
                  }
                }
              ]
            }""", """
            {
              "users": [
                {
                  "id": "alice",
                  "relations": {
                    "Manager": [null]
                  }
                }
              ]
            }"""})
    void testPutNullValues(String config) throws Exception {
        checkResponseWithPayload(OK, PUT, REPO_ID, SECURITY_POLICY_PAYLOAD, SecurityPolicyAPI.API_PATH);
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, config, getRelationsPath());
    }

    @Test
    void testGetStatus() throws Exception {
        checkResponseWithPayload(OK, PUT, REPO_ID, SECURITY_POLICY_PAYLOAD, SecurityPolicyAPI.API_PATH);
        checkResponseWithPayload(NO_CONTENT, PUT, REPO_ID, RELATIONS_PAYLOAD, getRelationsPath());
        var response = checkResponse(OK, GET, REPO_ID, getRelationsStatusPath());
        JSONObject json = new JSONObject(response.andReturn().getResponse().getContentAsString());
        assertThat(json.getInt(ExternalUserRelationsSecurityPolicyAPI.STATUS_COUNT_KEY)).isEqualTo(2);
        Instant lastModified = Instant.parse(
                json.getString(ExternalUserRelationsSecurityPolicyAPI.STATUS_LAST_MODIFIED_KEY));
        assertThat(lastModified.isAfter(Instant.EPOCH)).isTrue();
    }

    @Test
    void testGetStatusMissing() throws Exception {
        checkResponse(NOT_FOUND, GET, REPO_ID, getRelationsStatusPath());
    }

    String getRelationsPath() {
        return "%s/%s/externaluserrelations".formatted(SecurityPolicyAPI.API_PATH, POLICY);
    }

    String getRelationsStatusPath() {
        return "%s/status".formatted(getRelationsPath());
    }

}
