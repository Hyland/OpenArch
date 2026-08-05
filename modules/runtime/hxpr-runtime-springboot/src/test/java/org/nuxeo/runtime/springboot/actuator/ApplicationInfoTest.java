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
package org.nuxeo.runtime.springboot.actuator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.nuxeo.runtime.springboot.config.HxprRuntimeSpringbootAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.SpringVersion;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;


@SpringJUnitConfig
@SpringBootTest(classes = HxprRuntimeSpringbootAutoConfiguration.class)
@TestPropertySource(properties = {
        "springboot.app.version=1.0",
        "springboot.app.name=MyAutoWiredApp",
        "springboot.app.productVersion=2.0"
})
@EnableConfigurationProperties(ApplicationInfo.class)
public class ApplicationInfoTest {

    private static final String UNKNOWN = "unknown";

    @Test
    public void testApplicationInfoBean(@Autowired ApplicationInfo applicationInfo) {
        // Arrange - ApplicationInfo created by Spring above

        // Assert
        assertEquals("1.0", applicationInfo.version());
        assertEquals("MyAutoWiredApp", applicationInfo.name());
        assertEquals("2.0", applicationInfo.productVersion());
        assertEquals(SpringVersion.getVersion(), applicationInfo.springVersion());
    }

    @Test
    public void testApplicationInfo() {
        // Arrange
        ApplicationInfo applicationInfo = new ApplicationInfo("MyApp","1.0", "2.0");

        // Assert
        assertEquals("1.0", applicationInfo.version());
        assertEquals("MyApp", applicationInfo.name());
        assertEquals("2.0", applicationInfo.productVersion());
        assertEquals(SpringVersion.getVersion(), applicationInfo.springVersion());
    }

    @Test
    public void testApplicationInfoUnknowns() {
        // Arrange
        ApplicationInfo applicationInfo = new ApplicationInfo(null, null, null, null);

        // Assert
        assertEquals(UNKNOWN, applicationInfo.version());
        assertEquals(UNKNOWN, applicationInfo.name());
        assertEquals(UNKNOWN, applicationInfo.productVersion());
        assertEquals(UNKNOWN, applicationInfo.springVersion());
    }

}
