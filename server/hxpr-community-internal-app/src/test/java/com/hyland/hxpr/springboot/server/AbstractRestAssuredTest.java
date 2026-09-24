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
package com.hyland.hxpr.springboot.server;

import static io.restassured.RestAssured.given;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER_NAME;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * Abstract class to be extended by all rest assured tests.
 * Provides the initialization of the port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = HxprCommunityApplication.class)
@TestPropertySource(properties = "spring.profiles.active=default,local")
public abstract class AbstractRestAssuredTest {

    // "testuser"'s UUID (in identity-resources.json)
    protected static final String USER_NAME = TEST_ADMIN_USER_NAME;

    @LocalServerPort
    String port;

    @BeforeEach
    public void setUp() {
        RestAssured.port = Integer.parseInt(port);
    }

    /**
     * Returns a {@link RequestSpecification} pre-configured with the appropriate authentication for the current
     * deployment mode:
     * <ul>
     *   <li>HTTP Basic Auth ({@code testuser}/{@code password})</li>
     * </ul>
     */
    protected RequestSpecification authenticated() {
        return given().auth().preemptive().basic(USER_NAME, "password");
    }

}
