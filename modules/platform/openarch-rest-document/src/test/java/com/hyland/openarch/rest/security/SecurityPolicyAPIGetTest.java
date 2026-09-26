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
package com.hyland.openarch.rest.security;

import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static com.hyland.openarch.rest.utils.Headers.HXCS_REPOSITORY;
import static org.springframework.http.HttpMethod.GET;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.local.WithUser;
import org.springframework.http.HttpHeaders;

class SecurityPolicyAPIGetTest extends AbstractSecurityPolicyAPITest {

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    void testGetUnauthenticated() throws Exception {
        checkUnauthorizedGetWithCsrf();
    }

    @Test
    void testGetBadRepo() throws Exception {
        checkResponse(NOT_FOUND, GET, BAD_REPO_ID, SecurityPolicyAPI.API_PATH);
    }

    @Test
    @WithUser(ALT_USER)
    void testGet() throws Exception {
        var result = checkResponse(OK, GET, REPO_ID, SecurityPolicyAPI.API_PATH);
        checkResponseBodyEquals(ORIGINAL_POLICIES, result);
    }

    @Test
    @WithUser(ALT_USER)
    void testGetV1() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(CONTENT_TYPE, APPLICATION_JSON);
        headers.add(HXCS_REPOSITORY, REPO_ID);
        var result = checkResponse(OK, GET, headers, SecurityPolicyAPI.API_PATH);
        checkResponseBodyEquals(ORIGINAL_POLICIES, result);
    }

    @Test
    @WithUser(ALT_USER)
    void testGetV0() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(ACCEPT, "application/vnd.hxcs.v0+json");
        headers.add(HXCS_REPOSITORY, REPO_ID);
        checkResponse(NOT_ACCEPTABLE, GET, headers, SecurityPolicyAPI.API_PATH);
    }
}
