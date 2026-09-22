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
package org.nuxeo.rest.auth;

import static org.nuxeo.rest.auth.TestEndpoint.TEST_AUTH_ENDPOINT_PATH;
import static org.nuxeo.rest.auth.TestEndpoint.USER_PROP;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = { TestEndpoint.class, DenySystemSecurityPolicy.class })
public class TestSystemDenySecurityPolicy extends RuntimeSpringBootTest {

    protected static final String USER = "regularUser";

    protected static final String SYSTEM_USER = SecurityConstants.SYSTEM_USERNAME;

    /**
     * Rebuilds the {@link org.springframework.test.web.servlet.MockMvc} after the base class setup so that the
     * {@link DenySystemSecurityPolicy} filter (a servlet {@code Filter} bean) is part of the chain, replicating the
     * behaviour previously provided by {@code @AutoConfigureMockMvc}. The {@code NuxeoLoginFilter} is intentionally
     * left out: this test drives authentication through {@code @WithMockUser} rather than a real Nuxeo login.
     */
    @BeforeEach
    public void setupDenyFilter(@Autowired WebApplicationContext context, @Autowired DenySystemSecurityPolicy denyFilter) {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                             .apply(SecurityMockMvcConfigurers.springSecurity())
                             .addFilters(denyFilter)
                             .build();
    }

    @Test
    @WithMockUser(USER)
    void testRegularAuthentication() throws Exception {
         mvc.perform(get(TEST_AUTH_ENDPOINT_PATH))
            .andExpectAll(status().isOk(),
                content().json("{\"%s\":\"%s\"}".formatted(USER_PROP, USER)));
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
