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

import static jakarta.ws.rs.core.Response.Status.OK;
import static com.hyland.openarch.rest.security.SecurityPolicyAPI.API_PATH;
import static com.hyland.openarch.rest.utils.Headers.HXCS_REPOSITORY;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyland.openarch.rest.document.test.AbstractAPITest;
import com.hyland.openarch.runtime.test.runner.Deploy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;


@Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-security-policy.xml")
@SpringBootTest(classes = { SecurityPolicyAPI.class, SecurityPolicyManager.class, SecurityPolicyConfigMapper.class })
@TestPropertySource(properties = "logging.level.com.hyland.openarch.rest.security.SecurityPolicyManager=INFO")
public abstract class AbstractSecurityPolicyAPITest extends AbstractAPITest {

    protected static final String API_PROJECT_PATH = API_PATH + PROJECT_PF_00000;

    protected static final String ORIGINAL_POLICIES = "{}";

    protected static final String NEW_POLICY = """
            {
              "security clearance": {
                "engine": "securitylevel",
                "mixin": "Clearance",
                "contentTypes": ["TestDocument"],
                "permissions": ["Everything"],
                "userAttribute": "clearance"
              }
            }
            """;

    protected static final String NEW_PROJECT_POLICIES = """
            {
              "project A security policy 1": {
                "engine": "securitylevel",
                "mixin": "Clearance1",
                "contentTypes": ["TestDocument"],
                "permissions": ["Everything"],
                "userAttribute": "clearance",
                "projectId": "A"
              },
              "project A security policy 2": {
                "engine": "securitylevel",
                "mixin": "Clearance2",
                "contentTypes": ["TestDocument"],
                "permissions": ["Everything"],
                "userAttribute": "clearance",
                "projectId": "A"
              },
              "project xxx security policy": {
                "engine": "securitylevel",
                "mixin": "Clearance3",
                "contentTypes": ["TestDocument"],
                "permissions": ["Everything"],
                "userAttribute": "clearance",
                "projectId": "xxx"
              },
              "system-wide security policy": {
                "engine": "securitylevel",
                "mixin": "Clearance4",
                "contentTypes": ["TestDocument"],
                "permissions": ["Everything"],
                "userAttribute": "clearance"
              }
            }
            """.replace("xxx", PF_00000);

    protected static final String UPDATED_USER_ATTRIBUTE = """
            {
              "security clearance": {
                "engine": "securitylevel",
                "mixin": "Clearance",
                "contentTypes": ["TestDocument"],
                "permissions": ["Everything"],
                "userAttribute": "user_clearance"
              }
            }
            """;

    protected static final String UPDATED_PROJECT = """
            {
              "security clearance": {
                "mixin": "Clearance",
                "contentTypes": ["TestDocument"],
                "permissions": ["Everything"],
                "engine": "securitylevel",
                "projectId": "PF00000",
                "userAttribute": "clearance"
              }
            }
            """;

    protected interface MakeAChange {
        void makeAChange(String original) throws Exception;
    }

    protected void resetAfterChange(MakeAChange change) throws Exception {
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        String original = result.andReturn().getResponse().getContentAsString();
        try {
            change.makeAChange(original);
        } finally {
            result = checkResponseWithPayload(OK, PUT, REPO_ID, original, API_PATH);
            checkResponseBodyEquals(ORIGINAL_POLICIES, result);
        }
    }

    protected void assertNoChange(String originalConfig) throws Exception {
        var result = checkResponse(OK, GET, REPO_ID, API_PATH);
        checkResponseBodyEquals(originalConfig, result);
    }

    protected void checkUnauthorizedPutWithCsrf(String path) throws Exception {
        mvc.perform(put(path).header(HXCS_REPOSITORY, REPO_ID).content("{}").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    protected void checkUnauthorizedPatchWithCsrf(String path) throws Exception {
        mvc.perform(patch(path).header(HXCS_REPOSITORY, REPO_ID).content("[]").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    protected void checkUnauthorizedGetWithCsrf() throws Exception {
        mvc.perform(get(API_PATH).header(HXCS_REPOSITORY, REPO_ID).with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
