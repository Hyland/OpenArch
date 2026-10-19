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
package com.hyland.openarch.springboot.server;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.hyland.openarch.springboot.AbstractSpringTest;
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

    @Test
    void testBasicAuthenticationIsPublished() {
        var document = when().get("/v3/api-docs").then().statusCode(200).extract().jsonPath();

        assertThat(document.getString("components.securitySchemes.BasicAuth.type")).isEqualTo("http");
        assertThat(document.getString("components.securitySchemes.BasicAuth.scheme")).isEqualTo("basic");
        assertThat(document.<Map<String, Object>>getList("security"))
                .containsExactly(Map.of("SecurityScheme", List.of()), Map.of("BasicAuth", List.of()));
    }

}
