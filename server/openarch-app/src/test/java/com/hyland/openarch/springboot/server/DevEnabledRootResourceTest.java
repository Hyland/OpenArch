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

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.runtime.springboot.actuator.ApplicationInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = { "info.pages.debug.enabled=true", "management.endpoint.info.enabled=true"  })
@Order(Integer.MAX_VALUE)
public class DevEnabledRootResourceTest extends AbstractRestAssuredTest {

    @Autowired
    ApplicationInfo applicationInfo;

    @Test
    public void testIndexPage() {
        given().get("")
               .then()
               .statusCode(200)
               .body(allOf(containsString("Hyland Experience Platform Repository Community Version"),
                       not(containsString("Page not available"))));
    }

    @Test
    public void testJsonInfo() {
        given().get("actuator/info")
               .then()
               .statusCode(200)
               .body("name", is(applicationInfo.name()), //
                       "version", is(applicationInfo.version()), //
                       "productVersion", is(applicationInfo.productVersion()), //
                       "springVersion", is(applicationInfo.springVersion()));
    }

}
