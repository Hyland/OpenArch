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
import static org.hamcrest.Matchers.is;

import com.hyland.openarch.springboot.AbstractSpringTest;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import org.junit.jupiter.api.Test;

/**
 * Checks global application startup.
 *
 */
public class StartupTest extends AbstractSpringTest {

    @Test
    public void testHealthCheck() {
        try {
            RestAssured.defaultParser = Parser.JSON;
            when().get("/actuator/health")
                  .then()
                  .statusCode(200)
                  .assertThat()
                  .body("status", is("UP"));
        } finally {
            RestAssured.reset();
        }
    }

}
