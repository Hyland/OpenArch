/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *     Bogdan Stefanescu
 *     Anahide Tchertchian
 */
package org.nuxeo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentManager;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

/**
 * Test component manager events
 *
 * @since 9.2
 */
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.runtime.test.tests:component-manager-listener-dep.xml")
@Deploy("org.nuxeo.runtime.test.tests:component-manager-listener2.xml")
public class TestComponentManager {

    protected MyListener listener = new MyListener();

    public void checkCounters(MockEventsInfo info, int beforeStart, int afterStart, int beforeStop) {
        assertEquals(beforeStart, info.beforeStart);
        assertEquals(afterStart, info.afterStart);
        assertEquals(beforeStop, info.beforeStop);
    }

    protected MockEventsInfo checkMockComponentManagerListener2(boolean isNull) {
        // this listener should be automatically registered
        MockComponentManagerListener2 listener2 = (MockComponentManagerListener2) Framework.getRuntime()
                                                                                           .getComponent(
                                                                                                   MockComponentManagerListener2.NAME);
        if (isNull) {
            assertNull(listener2);
            return null;
        } else {
            assertNotNull(listener2);
            return listener2.info;
        }
    }

    public void checkCounters2(int beforeStart, int afterStart, int beforeStop) {
        MockEventsInfo info = checkMockComponentManagerListener2(false);
        checkCounters(info, beforeStart, afterStart, beforeStop);
    }

    @Test
    public void testManagerEvents() throws Exception {
        ComponentManager mgr = Framework.getRuntime().getComponentManager();
        mgr.addListener(listener);
        checkCounters(listener.info, 0, 0, 0);
        checkCounters2(1, 1, 0);
        mgr.restart(false);
        checkCounters(listener.info, 1, 1, 1);
        checkCounters2(1, 1, 0);
        mgr.restart(true);
        checkCounters(listener.info, 2, 2, 2);
        checkCounters2(1, 1, 0);
        mgr.refresh();
        checkCounters(listener.info, 2, 2, 2);
        checkCounters2(1, 1, 0);
        mgr.stop();
        checkCounters(listener.info, 2, 2, 3);
        checkMockComponentManagerListener2(true);
        mgr.start();
        checkCounters(listener.info, 3, 3, 3);
        checkCounters2(1, 1, 0);
    }

    protected static class MyListener implements ComponentManager.Listener {

        public MockEventsInfo info = new MockEventsInfo();

        @Override
        public void beforeRuntimeStop(ComponentManager mgr) {
            info.beforeStop++;
        }

        @Override
        public void beforeRuntimeStart(ComponentManager mgr) {
            info.beforeStart++;
        }

        @Override
        public void afterRuntimeStart(ComponentManager mgr) {
            info.afterStart++;
        }

    }

}
