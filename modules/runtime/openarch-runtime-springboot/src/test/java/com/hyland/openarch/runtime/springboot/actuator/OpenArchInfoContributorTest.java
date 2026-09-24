/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.springboot.actuator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.Info.Builder;


public class OpenArchInfoContributorTest {
    @Test
    void testContribute() {
        ApplicationInfo applicationInfo = new ApplicationInfo("MyApp","1.0", "2.0", "3.0");
        OpenArchInfoContributor openArchInfoContributor = new OpenArchInfoContributor(applicationInfo);
        Info.Builder builder = new Builder();

        openArchInfoContributor.contribute(builder);

        assertEquals("MyApp", builder.build().get("name"));
        assertEquals("1.0", builder.build().get("version"));
        assertEquals("2.0", builder.build().get("productVersion"));
        assertEquals("3.0", builder.build().get("springVersion"));
    }
}
