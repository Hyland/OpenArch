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
package org.nuxeo.runtime.springboot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentManager;
import org.nuxeo.runtime.springboot.config.NuxeoApplicationConfiguration;
import org.nuxeo.runtime.springboot.mock.IMockComponent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = { NuxeoApplicationConfiguration.class })
@TestPropertySource(locations = { "classpath:application.properties" })
public class NuxeoRuntimeSpringBootTest {

    @Test
    public void testMockComponent() {
        ComponentManager cm = Framework.getRuntime().getComponentManager();
        assertNotNull(cm);
        assertNotNull(cm.getComponent("org.nuxeo.runtime.springboot.MockComponent"));
        IMockComponent comp = Framework.getService(IMockComponent.class);
        assertNotNull(comp);
        assertEquals("Set by application.properties", comp.getPropertyValue());
        assertEquals("Set by application.properties Reference", comp.getReferencePropertyValue());
        assertEquals("Set by application.properties Reference", comp.getReferenceProperty2Value());
        // check that sys source prop references are still resolved
        assertEquals(System.getProperty("java.specification.version"), comp.getSysPropertyValue());
    }

}
