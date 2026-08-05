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
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;

import com.hyland.hxpr.springboot.AbstractSpringTest;
import jakarta.ws.rs.core.Response.Status;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.runtime.springboot.NuxeoApplication;

public class GreetingResourceTest extends AbstractSpringTest {

    // not referenced in embedded user test configuration
    protected static final String UNDEFINED_USERNAME = "undefined";

    protected static final String SYSTEM = SecurityConstants.SYSTEM_USERNAME;

    public static final String ANONYMOUS_USER = "anonymous";

    protected String getMessage(String username) {
        return String.format(GreetingResourceController.HELLO_MESSAGE_PATTERN, username, NuxeoApplication.STATUS_STARTED);
    }

    protected static @NonNull String getExpectedUser() {
        return USER_NAME;
    }

    @Test
    public void testHelloEndpoint() {
        String path = "/hello";
        given().get(path).then().statusCode(Status.OK.getStatusCode()).body(is(getMessage(ANONYMOUS_USER)));
        authenticated().when()
                       .get(path)
                       .then()
                       .statusCode(Status.OK.getStatusCode())
                       .body(is(getMessage(getExpectedUser())));
        // In Basic Auth mode, wrong credentials return 401 instead.
        given().auth()
               .preemptive()
               .basic(UNDEFINED_USERNAME, UNDEFINED_USERNAME)
               .when()
               .get(path)
               .then()
               .statusCode(Status.UNAUTHORIZED.getStatusCode());
        given().auth()
               .preemptive()
               .basic(SYSTEM, SYSTEM)
               .when()
               .get(path)
               .then()
               .statusCode(Status.UNAUTHORIZED.getStatusCode());
    }

    @Test
    public void testHelloProtectedEndpoint() {
        String path = "/hello/protected";
        given().get(path).then().statusCode(Status.UNAUTHORIZED.getStatusCode());
        authenticated().when()
                       .get(path)
                       .then()
                       .statusCode(Status.OK.getStatusCode())
                       .body(is(getMessage(getExpectedUser())));
        given().auth()
               .preemptive()
               .basic(UNDEFINED_USERNAME, UNDEFINED_USERNAME)
               .when()
               .get(path)
               .then()
               .statusCode(Status.UNAUTHORIZED.getStatusCode());
        given().auth()
               .preemptive()
               .basic(SYSTEM, SYSTEM)
               .when()
               .get(path)
               .then()
               .statusCode(Status.UNAUTHORIZED.getStatusCode());
    }

    @Test
    public void testHelloOpenAPI() {
        when().get("/v3/api-docs")
              .then()
              .body(containsString("\"name\":\"Greeting\""),
                  containsString("\"description\":\"Hello World Endpoint for HXPR Community\""));
    }

}
