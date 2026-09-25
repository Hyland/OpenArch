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
package com.hyland.openarch.ecm.platform.login.filestore;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.platform.login.filestore.config.FileStoreUsersProperties;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Verifies that {@link FileStoreSecurityConfiguration} enforces HTTP Basic Auth end-to-end: valid credentials are
 * accepted, missing or wrong credentials are rejected with 401. Uses the bundled {@code hxpr-users.yml} test fixture
 * (alice / secret via test-users.yml).
 */
@SpringBootTest(classes = {
        BasicAuthSecurityTest.TestApp.class }, properties = "hxpr.users.file=classpath:test-users.yml")
class BasicAuthSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                                      .apply(SecurityMockMvcConfigurers.springSecurity())
                                      .build();
    }

    private static String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void authenticatedRequest_withValidCredentials_returns200() throws Exception {
        // alice / secret are defined in test-users.yml
        mockMvc.perform(get("/ping").header(HttpHeaders.AUTHORIZATION, basicAuth("alice", "secret")))
               .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(get("/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPassword_returns401() throws Exception {
        mockMvc.perform(get("/ping").header(HttpHeaders.AUTHORIZATION, basicAuth("alice", "wrong")))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUser_returns401() throws Exception {
        mockMvc.perform(get("/ping").header(HttpHeaders.AUTHORIZATION, basicAuth("nobody", "secret")))
               .andExpect(status().isUnauthorized());
    }

    @SpringBootConfiguration
    @EnableWebMvc
    @EnableConfigurationProperties(FileStoreUsersProperties.class)
    @Import({ FileStoreSecurityConfiguration.class })
    static class TestApp {

        @Bean
        UserStoreLoader userStoreLoader(FileStoreUsersProperties properties) {
            return new UserStoreLoader(properties, new DefaultResourceLoader());
        }

        @RestController
        static class PingController {
            @GetMapping("/ping")
            String ping() {
                return "pong";
            }
        }
    }
}
