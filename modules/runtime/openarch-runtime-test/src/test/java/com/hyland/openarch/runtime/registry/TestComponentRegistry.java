/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.registry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.ComponentManager;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
public class TestComponentRegistry {

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:CompA.xml")
    public void testA() throws Exception {
        // requires CompB and CompC
        ComponentManager componentManager = Framework.getRuntime().getComponentManager();
        assertNull(componentManager.getComponent("CompA"));
    }

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:CompA.xml")
    @Deploy("org.nuxeo.runtime.test.tests:CompB.xml")
    public void testAB() throws Exception {
        // still requires CompC
        ComponentManager componentManager = Framework.getRuntime().getComponentManager();
        assertNull(componentManager.getComponent("CompA"));
    }

    @Test
    @Deploy("org.nuxeo.runtime.test.tests:CompA.xml")
    @Deploy("org.nuxeo.runtime.test.tests:CompB.xml")
    @Deploy("org.nuxeo.runtime.test.tests:CompC.xml")
    public void testABC() throws Exception {
        ComponentManager componentManager = Framework.getRuntime().getComponentManager();
        assertNotNull(componentManager.getComponent("CompA"));
        assertNotNull(componentManager.getComponent("CompB"));
        assertNotNull(componentManager.getComponent("CompC"));
    }

    /**
     * Deploy then reset
     */
    @Test
    @Deploy("org.nuxeo.runtime.test.tests:CompC.xml")
    public void testReset() throws Exception {
        ComponentManager componentManager = Framework.getRuntime().getComponentManager();
        assertNotNull(componentManager.getComponent("CompC"));

        // test the reset instead of unregsitering each component
        Framework.getRuntime().getComponentManager().reset();
        Framework.getRuntime().getComponentManager().start();

        assertNull(componentManager.getComponent("CompC"));
    }

}
