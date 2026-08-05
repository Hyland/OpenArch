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
package org.nuxeo.runtime.springboot.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.security.auth.login.LoginException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.springboot.auth.test.config.NuxeoLoginTestEndpointConfig;
import org.nuxeo.runtime.springboot.config.HxprRuntimeSpringbootAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = { HxprRuntimeSpringbootAutoConfiguration.class, NuxeoLoginTestEndpointConfig.class })
public class NuxeoLoginFilterTest {

    protected static final String USER = "user";

    @MockitoSpyBean
    NuxeoLoginFilter nuxeoLoginFilter;

    MockMvc mvc;

    @BeforeEach
    public void setup(@Autowired WebApplicationContext context, @Autowired NuxeoLoginFilter interceptor) {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                             .apply(SecurityMockMvcConfigurers.springSecurity())
                             .addFilters(interceptor)
                             .build();
    }

    @Test
    @WithMockUser(username = USER)
    void testAuthenticated() throws Exception {
        mvc.perform(get("/test/hello")).andExpect(status().isOk()).andExpect(content().string("Hello user"));
        verify(nuxeoLoginFilter).doFilterWithLogin(any(), any(), any());
    }

    @Test
    void testAnonymous() throws Exception {
        mvc.perform(get("/test/hello")).andExpect(status().isOk()).andExpect(content().string("Hello Anonymous"));
        verify(nuxeoLoginFilter).doFilterInternal(any(), any(), any()); // Filter is called even for anonymous
        verify(nuxeoLoginFilter, times(0)).doFilterWithLogin(any(), any(), any()); // but login is not called
    }

    @Test
    void testNotAuthenticated() throws Exception {
        mvc.perform(get("/test/secured")).andExpect(status().isUnauthorized());
        verifyNoInteractions(nuxeoLoginFilter); // Spring Security should block the request before the filter is called
    }

    @Test
    @WithMockUser(username = USER)
    void testUserWithNuxeoException() throws Exception {
        MockedStatic<Framework> frameworkMock = mockStatic(Framework.class);
        frameworkMock.when(Framework::isInitialized).thenReturn(true);
        frameworkMock.when(() -> Framework.loginUser(any()))
                     .thenThrow(new NuxeoException("Subscription Token Exchange fail!", 429));
        mvc.perform(get("/test/exception")).andExpect(status().isTooManyRequests());
        frameworkMock.close();
    }

    @Test
    @WithMockUser(username = USER)
    void testUserWithLoginException() throws Exception {
        MockedStatic<Framework> frameworkMock = mockStatic(Framework.class);
        frameworkMock.when(Framework::isInitialized).thenReturn(true);
        frameworkMock.when(() -> Framework.loginUser(any()))
                     .thenThrow(new LoginException("principal " + USER + " does not exist"));
        mvc.perform(get("/test/exception")).andExpect(status().isUnauthorized());
        frameworkMock.close();
    }

}
