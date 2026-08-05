/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.runtime.springboot.junit.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.springboot.junit.RuntimeSpringBootTest;
import org.nuxeo.runtime.springboot.junit.RuntimeTestState;
import org.nuxeo.runtime.springboot.junit.test.TestRuntimeSpringBootTest.InjectingFeature;
import org.nuxeo.runtime.springboot.mock.IMockComponent;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesLoader;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.WithApplicationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import com.google.inject.Binder;

/**
 * Checks that deployed test component is present on the test.
 * <p>
 * Note We are reusing the {@link IMockComponent} from other tests, as we were previously using the Quarkus class loader
 * (test dependencies were banned by Quarkus class loader). When migrating to Spring boot there was no need to change
 * this.
 *
 */
@Features({ RuntimeFeature.class, InjectingFeature.class })
@Deploy("org.nuxeo.runtime.springboot.tests:OSGI-INF/test-component.xml")
@WithApplicationProperties("test-application.properties")
@SpringBootTest(classes = RuntimeTestState.class)
public class TestRuntimeSpringBootTest extends RuntimeSpringBootTest {

    @RuntimeInject
    FeaturesLoader injectedLoader;

    @RuntimeInject
    IMockComponent injectedComponent;

    @RuntimeInject
    RuntimeFeature injectedFeature;

    @RuntimeInject
    TestInstance injectedInstance;

    @Test
    public void testComponent() {
        IMockComponent comp = Framework.getService(IMockComponent.class);
        assertNotNull(comp);
        assertEquals("testiing", comp.getPropertyValue());
    }

    @Test
    public void testInjections() {
        assertNotNull(injectedLoader);
        assertNotNull(injectedComponent);
        assertNotNull(injectedFeature);
        assertNotNull(injectedInstance);
        assertEquals("beforeMethodRun", injectedInstance.getId());
    }

    public static class InjectingFeature implements RunnerFeature {

        protected TestInstance instance;

        @Override
        public void configure(Binder binder) {
            binder.bind(TestInstance.class).toProvider(() -> instance);
        }

        @Override
        public void beforeSetup(Method method, Object test) {
            instance = new TestInstance("init");
        }

        @Override
        public void beforeMethodRun(Method method, Object test) {
            instance = new TestInstance("beforeMethodRun");
        }

        @Override
        public void afterMethodRun(Method method, Object test) {
            instance = null;
        }

    }

    public static class TestInstance {

        protected String id;

        public TestInstance(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

    }

}
