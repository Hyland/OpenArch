/*
 * (C) Copyright 2026 Hyland (http://www.hyland.com/) and others.
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
package com.hyland.hxpr.springboot.server;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Basic check on index page.
 *
 */
public class RootResourceTest extends AbstractRestAssuredTest {

    @Test
    public void testIndexPage() {
        given().get("").then().statusCode(HttpStatus.OK.value()).body(containsString("Page not available"));
    }

    @Test
    public void testJsonInfo() {
        given().get("actuator/info").then().statusCode(HttpStatus.NOT_FOUND.value());
    }

}
