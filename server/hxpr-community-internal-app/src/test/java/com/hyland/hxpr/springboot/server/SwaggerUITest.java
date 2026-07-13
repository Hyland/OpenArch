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

import static io.restassured.RestAssured.when;

import com.hyland.hxpr.springboot.AbstractSpringTest;
import org.junit.jupiter.api.Test;

/**
 * Checks Swagger UI is always present.
 *
 */
class SwaggerUITest extends AbstractSpringTest {

    @Test
    void testSwaggerUI() {
        when().get("/swagger-ui/index.html").then().statusCode(200);
    }

}
