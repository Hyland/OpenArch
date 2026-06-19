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
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.auth;

import static org.nuxeo.rest.auth.TestEndpoint.TEST_AUTH_ENDPOINT_PATH;
import static org.nuxeo.rest.auth.TestEndpoint.USER_PROP;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = { TestEndpoint.class, DenySystemSecurityPolicy.class })
@AutoConfigureMockMvc
public class TestSystemDenySecurityPolicy  {

    protected static final String USER = "regularUser";

    protected static final String SYSTEM_USER = SecurityConstants.SYSTEM_USERNAME;

    @Autowired
    MockMvc mvc;

    @Test
    @WithMockUser(USER)
    void testRegularAuthentication() throws Exception {
         mvc.perform(get(TEST_AUTH_ENDPOINT_PATH))
            .andExpectAll(status().isOk(),
                content().json("{%s:%s}".formatted(USER_PROP, USER)));
    }

    @Test
    @WithMockUser(SYSTEM_USER)
    void testSystemAuthentication() throws Exception {
        mvc.perform(get(TEST_AUTH_ENDPOINT_PATH))
            .andExpect(status().isForbidden());
    }

    @Test
    void testNoAuthentication() throws Exception {
        mvc.perform(get(TEST_AUTH_ENDPOINT_PATH))
            .andExpect(status().isUnauthorized());
    }

}
