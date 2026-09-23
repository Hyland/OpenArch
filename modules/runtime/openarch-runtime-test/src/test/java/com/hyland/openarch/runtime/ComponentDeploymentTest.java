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
package com.hyland.openarch.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.model.ComponentInstance;
import com.hyland.openarch.runtime.model.ComponentManager;
import com.hyland.openarch.runtime.model.Extension;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.test.runner.RuntimeFeature;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
public class ComponentDeploymentTest {

    @Test
    @Deploy("com.hyland.openarch.runtime.test.tests:MyComp1.xml")
    @Deploy("com.hyland.openarch.runtime.test.tests:MyComp2.xml")
    public void testContributions() throws Exception {
        RuntimeService runtime = Framework.getRuntime();
        ComponentManager mgr = runtime.getComponentManager();
        assertTrue(mgr.size() > 0);

        ComponentInstance co = runtime.getComponentManager().getComponent("my.comp1");
        assertNotNull(co);
        assertEquals(co.getName(), "my.comp1");

        co = runtime.getComponentManager().getComponent("my.comp2");
        assertNotNull(co);
        assertEquals(co.getName(), "my.comp2");
    }

    @Test
    @Deploy("com.hyland.openarch.runtime.test.tests:CompA.xml")
    @Deploy("com.hyland.openarch.runtime.test.tests:CompB.xml")
    public void testStartupStatus() throws Exception {
        RuntimeService runtime = Framework.getRuntime();
        ComponentManager mgr = runtime.getComponentManager();
        assertTrue(mgr.size() > 0);
        // check pending registrations
        Map<String, Set<String>> pending = mgr.getPendingRegistrations();
        assertEquals(1, pending.size());
        assertTrue(pending.containsKey("CompA"));
        assertEquals("[CompC]", pending.get("CompA").toString());
        // check missing registrations
        Map<String, Set<Extension>> missing = mgr.getMissingRegistrations();
        assertEquals(1, missing.size());
        assertTrue(missing.containsKey("CompB"));
        assertEquals(
                "[ExtensionImpl {target: my.comp3, point:xp, contributor:RegistrationInfo: CompB}, "
                        + "ExtensionImpl {target: my.comp4, point:xp, contributor:RegistrationInfo: CompB}]",
                missing.get("CompB").toString());
        StringBuilder builder = new StringBuilder();
        assertTrue(runtime.getStatusMessage(builder));
        String status = String.format(
                "======================================================================\n"
                        + "= Component Loading Status: Pending: 1 / Missing: 1 / Unstarted: 0 / Total: %s\n"
                        + "  * CompA requires [CompC]\n"
                        + "  * CompB references missing [target=my.comp3;point=xp, target=my.comp4;point=xp]\n"
                        + "======================================================================",
                mgr.getRegistrations().size());
        assertEquals(status, builder.toString());
    }

    @Test
    public void testStartupOk() throws Exception {
        RuntimeService runtime = Framework.getRuntime();
        assertFalse(runtime.hasErrors());
        ComponentManager mgr = runtime.getComponentManager();
        StringBuilder builder = new StringBuilder();
        assertFalse(runtime.getStatusMessage(builder));
        String status = "%s Components Started".formatted(mgr.getRegistrations().size());
        assertEquals(status, builder.toString());
    }

    @Test
    @Deploy("com.hyland.openarch.runtime.test.tests:CompA.xml")
    @Deploy("com.hyland.openarch.runtime.test.tests:CompB.xml")
    public void testStartupErrors() throws Exception {
        assertTrue(Framework.getRuntime().hasErrors());
    }

}
